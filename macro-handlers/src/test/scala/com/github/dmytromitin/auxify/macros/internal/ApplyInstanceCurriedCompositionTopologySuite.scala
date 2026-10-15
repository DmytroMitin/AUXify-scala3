package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.termName
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome}

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

class ApplyInstanceCurriedCompositionTopologySuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val instanceName = "com.github.dmytromitin.auxify.macros.instance"
  private val admittedOrders = List(
    List(applyName, instanceName),
    List(instanceName, applyName)
  )

  test("both orders generate one apply and one strict curried factory") {
    admittedOrders.foreach { order =>
      withParsedComposition(richSource("TopologyCurried"), "TopologyCurried") {
        (primary, companion, currentAnnotation, context) =>
          given Context = context
          val originalTemplate = primary.rhs
          val output = runComposition(
            primary,
            companion,
            currentAnnotation,
            "TopologyCurried",
            order
          )

          assert(output.primary.rhs.eq(originalTemplate), clue(order))
          val terms = output.companion.toList.flatMap(_.impl.body).collect {
            case method: DefDef => method
          }
          assertEquals(terms.map(_.name.toString), order.map(shortName), clue(order))

          val instanceMethod = terms.find(_.name == termName("instance"))
            .getOrElse(fail("missing instance"))
          assertEquals(instanceMethod.leadingTypeParams.map(_.name.toString), List("A"))
          instanceMethod.trailingParamss match
            case List(List(carrier: ValDef)) =>
              assertEquals(carrier.name.toString, "combineFunction")
              carrier.tpt match
                case Function(
                      List(Ident(first)),
                      Function(List(Ident(second)), Ident(result))
                    ) =>
                  assertEquals(
                    List(first.toString, second.toString, result.toString),
                    List("A", "A", "A")
                  )
                case other => fail(s"expected strict nested carrier, found $other")
            case other => fail(s"expected one carrier parameter, found $other")
          instanceMethod.rhs match
            case New(template: Template) =>
              assertEquals(template.parentsOrDerived.size, 1)
              template.body match
                case List(method: DefDef) =>
                  assertEquals(method.name.toString, "combine")
                  assertEquals(
                    method.trailingParamss.map(_.map(_.name.toString)),
                    List(List("a"), List("b"))
                  )
                  method.rhs match
                    case Apply(
                          Apply(Ident(callee), List(Ident(first))),
                          List(Ident(second))
                        ) =>
                      assertEquals(callee.toString, "combineFunction")
                      assertEquals(first.toString, "a")
                      assertEquals(second.toString, "b")
                    case other => fail(s"expected nested carrier applications, found $other")
                case other => fail(s"expected only one anonymous override, found $other")
            case other => fail(s"expected one anonymous instance child, found $other")
      }
    }
  }

  test("carrier freshness uses the term namespace and ignores a same-spelling alias") {
    val source =
      """@current
        |trait Collision[Element]:
        |  def merge(combineFunction: Element)(combineFunction1: Element): Element
        |  type combineFunction3 = Element
        |  def keep(combineFunction2: Element): Element = combineFunction2
        |""".stripMargin
    withParsedComposition(source, "Collision") {
      (primary, companion, currentAnnotation, context) =>
        given Context = context
        val output = runComposition(
          primary,
          companion,
          currentAnnotation,
          "Collision",
          List(applyName, instanceName)
        )
        val instanceMethod = output.companion.toList.flatMap(_.impl.body).collectFirst {
          case method: DefDef if method.name == termName("instance") => method
        }.getOrElse(fail("missing collision-safe instance"))

        instanceMethod.trailingParamss match
          case List(List(carrier: ValDef)) =>
            assertEquals(carrier.name.toString, "combineFunction3")
          case other => fail(s"expected one collision-safe carrier, found $other")
        instanceMethod.rhs match
          case New(template: Template) =>
            assertEquals(
              template.body.collect { case method: DefDef => method.name.toString },
              List("merge")
            )
            assertEquals(template.body.collect { case _: TypeDef => 1 }, Nil)
          case other => fail(s"expected anonymous collision-safe child, found $other")
    }
  }

  test("both orders preserve independent apply and instance conflicts without duplicates") {
    for
      order <- admittedOrders
      existingApply <- List(false, true)
      existingInstance <- List(false, true)
    do
      val className =
        s"CurriedConflict${if existingApply then "Apply" else "NoApply"}${if existingInstance then "Instance" else "NoInstance"}"
      withParsedComposition(
        conflictSource(className, existingApply, existingInstance),
        className
      ) { (primary, companion, currentAnnotation, context) =>
        given Context = context
        val originals = companion.toList.flatMap(_.impl.body).collect {
          case method: DefDef if Set("apply", "instance").contains(method.name.toString) =>
            method.name.toString -> method
        }.toMap
        val output = runComposition(
          primary,
          companion,
          currentAnnotation,
          className,
          order
        )
        val methods = output.companion.toList.flatMap(_.impl.body).collect {
          case method: DefDef if Set("apply", "instance").contains(method.name.toString) => method
        }
        assertEquals(methods.count(_.name == termName("apply")), 1, clue(order))
        assertEquals(methods.count(_.name == termName("instance")), 1, clue(order))
        originals.foreach { case (name, original) =>
          assert(methods.find(_.name.toString == name).exists(_.eq(original)), clue((order, name)))
        }
      }
  }

  test("a reachable later lowering failure rolls back an earlier staged apply edit") {
    admittedOrders.foreach { order =>
      val source =
        s"""${richSource("AtomicCurried")}
           |
           |object AtomicCurried:
           |  val retained = 17
           |""".stripMargin
      withParsedComposition(source, "AtomicCurried") {
        (primary, companion, currentAnnotation, context) =>
          given Context = context
          val original = StructuredExpansionOutput(primary, companion)
          var staged = original
          var diagnostics = List.empty[String]
          var visited = List.empty[String]
          var applyWasStaged = false

          order.foreach: annotationName =>
            if diagnostics.isEmpty then
              visited = visited :+ annotationName
              val input = paradise3.api.ExpansionInputTestFactory(
                annotationName,
                staged.primary,
                staged.companion,
                Set("AtomicCurried"),
                Some(currentAnnotation),
                order
              )
              val outcome =
                if annotationName == applyName then new ApplyHandler().expand(input)
                else
                  InstanceHandler.expandWithLowering(input): (_, _) =>
                    Left(
                      InstanceFactoryPeerBridge.Failure(
                        "PROMPT101_CONTROLLED_LOWERING_FAILURE",
                        "no partial apply or instance edit may survive"
                      )
                    )
              outcome match
                case ExpansionOutcome.Structured(changes) =>
                  staged = StructuredOutcomeTestSupport.materialize(input, changes)
                  applyWasStaged = staged.companion.exists(_.impl.body.exists {
                    case method: DefDef => method.name == termName("apply")
                    case _ => false
                  })
                case ExpansionOutcome.Rejected(values) =>
                  diagnostics = values.map(_.message)
                case other => fail(s"expected structured change or rejection, found $other")

          val output = if diagnostics.nonEmpty then original else staged
          assertEquals(
            diagnostics,
            List(
              "PROMPT101_CONTROLLED_LOWERING_FAILURE: no partial apply or instance edit may survive"
            ),
            clue(order)
          )
          assertEquals(
            visited,
            if order.head == applyName then List(applyName, instanceName) else List(instanceName),
            clue(order)
          )
          assertEquals(applyWasStaged, order.head == applyName, clue(order))
          assert(output.primary.eq(original.primary), clue(order))
          assert(output.companion == original.companion, clue(order))
      }
    }
  }

  private def runComposition(
      primary: TypeDef,
      companion: Option[ModuleDef],
      currentAnnotation: Tree,
      className: String,
      order: List[String]
  )(using Context): StructuredExpansionOutput =
    order.foldLeft(StructuredExpansionOutput(primary, companion)) {
      case (staged, annotationName) =>
        val input = paradise3.api.ExpansionInputTestFactory(
          annotationName,
          staged.primary,
          staged.companion,
          Set(className),
          Some(currentAnnotation),
          order
        )
        val outcome =
          if annotationName == applyName then new ApplyHandler().expand(input)
          else new InstanceHandler().expand(input)
        outcome match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport.materialize(input, changes)
          case other => fail(s"expected structured composition for $order, found $other")
    }

  private def withParsedComposition[A](
      source: String,
      className: String
  )(run: (TypeDef, Option[ModuleDef], Tree, Context) => A): A =
    val unit = CompilationUnit(s"${className}Topology.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst {
      case value: ModuleDef if value.name.toString == className => value
    }
    val currentAnnotation = Trees.mods(primary).annotations.head
    run(primary, companion, currentAnnotation, summon[Context])

  private def richSource(className: String): String =
    s"""@current
       |trait $className[A]:
       |  def combine(a: A)(b: A): A
       |  type Item = A
       |  def twice(a: A): A = combine(a)(a)
       |  type Value = A
       |  def fold3(a: A, b: A, c: A): A = combine(combine(a)(b))(c)
       |""".stripMargin

  private def conflictSource(
      className: String,
      existingApply: Boolean,
      existingInstance: Boolean
  ): String =
    val applyDefinition =
      if existingApply then
        s"def apply[A](using value: $className[A]): $className[A] = value"
      else ""
    val instanceDefinition =
      if existingInstance then
        s"""def instance[A](f: A => A => A): $className[A] =
           |    new $className[A]:
           |      def combine(a: A)(b: A): A = f(a)(b)""".stripMargin
      else ""
    s"""@current
       |trait $className[A]:
       |  def combine(a: A)(b: A): A
       |
       |object $className:
       |  val retained = 1
       |  $applyDefinition
       |  $instanceDefinition
       |""".stripMargin

  private def shortName(annotationName: String): String =
    if annotationName == applyName then "apply" else "instance"
