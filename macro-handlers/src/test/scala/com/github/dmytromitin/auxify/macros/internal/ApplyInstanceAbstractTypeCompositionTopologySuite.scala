package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.termName
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome}

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

class ApplyInstanceAbstractTypeCompositionTopologySuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val instanceName = "com.github.dmytromitin.auxify.macros.instance"
  private val admittedOrders = List(
    List(applyName, instanceName),
    List(instanceName, applyName)
  )

  test("both orders generate two independent terms and one refined anonymous child") {
    admittedOrders.foreach { order =>
      withParsedComposition(richSource("TopologyHasOut"), "TopologyHasOut") {
        (primary, companion, currentAnnotation, context) =>
          given Context = context
          val originalTemplate = primary.rhs
          val output = runComposition(
            primary,
            companion,
            currentAnnotation,
            "TopologyHasOut",
            order
          )

          assert(output.primary.rhs.eq(originalTemplate), clue(order))
          val terms = output.companion.toList.flatMap(_.impl.body).collect {
            case method: DefDef => method
          }
          assertEquals(terms.map(_.name.toString), order.map(shortName), clue(order))

          val applyMethod = terms.find(_.name == termName("apply")).getOrElse(fail("missing apply"))
          val instanceMethod = terms.find(_.name == termName("instance")).getOrElse(fail("missing instance"))
          assertEquals(applyMethod.leadingTypeParams.map(_.name.toString), List("A"))
          assert(applyMethod.tpt.isInstanceOf[AppliedTypeTree], clue(applyMethod.tpt))
          assertEquals(instanceMethod.leadingTypeParams.map(_.name.toString), List("A", "Out0"))
          instanceMethod.tpt match
            case refinement: RefinedTypeTree =>
              assertEquals(
                refinement.refinements.collect { case alias: TypeDef => alias.name.toString },
                List("Out")
              )
            case other => fail(s"expected refined instance result for $order, found $other")
          instanceMethod.rhs match
            case New(template: Template) =>
              assertEquals(template.parentsOrDerived.size, 1)
              assertEquals(
                template.body.collect { case alias: TypeDef => alias.name.toString },
                List("Out")
              )
              assertEquals(
                template.body.collect {
                  case method: DefDef if method.name.toString != "<init>" => method.name.toString
                },
                Nil
              )
            case other => fail(s"expected one anonymous instance child for $order, found $other")
      }
    }
  }

  test("type binder freshness skips tail aliases but not the term namespace") {
    val source =
      """@current
        |trait Collision[Element]:
        |  type Result
        |  type Result0 = Result
        |  type Result1 = Element
        |  def Result2(value: Element): Element = value
        |""".stripMargin
    withParsedComposition(source, "Collision") {
      (primary, companion, currentAnnotation, context) =>
        given Context = context
        val input = paradise3.api.ExpansionInputTestFactory(
          instanceName,
          primary,
          companion,
          Set("Collision"),
          Some(currentAnnotation),
          List(applyName, instanceName)
        )
        val method = new InstanceHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport
              .materialize(input, changes)
              .companion
              .toList
              .flatMap(_.impl.body)
              .collectFirst {
                case value: DefDef if value.name == termName("instance") => value
              }
              .getOrElse(fail("missing collision-safe instance"))
          case other => fail(s"expected collision-safe instance, found $other")

        assertEquals(
          method.leadingTypeParams.map(_.name.toString),
          List("Element", "Result2")
        )
        method.rhs match
          case New(template: Template) =>
            assertEquals(
              template.body.collect { case alias: TypeDef => alias.name.toString },
              List("Result")
            )
            assertEquals(
              template.body.collect {
                case definition: DefDef if definition.name.toString != "<init>" =>
                  definition.name.toString
              },
              Nil
            )
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
        s"Conflict${if existingApply then "Apply" else "NoApply"}${if existingInstance then "Instance" else "NoInstance"}"
      withParsedComposition(
        conflictSource(className, existingApply, existingInstance),
        className
      ) { (primary, companion, currentAnnotation, context) =>
        given Context = context
        val originals = companion.toList.flatMap(_.impl.body).collect {
          case method: DefDef if Set("apply", "instance").contains(method.name.toString) =>
            method.name.toString -> method
        }.toMap
        val originalBody = companion.toList.flatMap(_.impl.body)
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
        val retainedBody = output.companion.toList.flatMap(_.impl.body)
          .filter(tree => originalBody.exists(_.eq(tree)))
        assertEquals(retainedBody.size, originalBody.size, clue(order))
        retainedBody.zip(originalBody).foreach { (retained, original) =>
          assert(retained.eq(original), clue(order))
        }
        assertEquals(methods.count(_.name == termName("apply")), 1, clue(order))
        assertEquals(methods.count(_.name == termName("instance")), 1, clue(order))
        originals.foreach { case (name, original) =>
          assert(methods.find(_.name.toString == name).exists(_.eq(original)), clue((order, name)))
        }
      }
  }

  test("a reachable later lowering failure rolls back earlier staged apply edits") {
    admittedOrders.foreach { order =>
      val source =
        s"""${richSource("AtomicHasOut")}
           |
           |object AtomicHasOut:
           |  val retained = 17
           |""".stripMargin
      withParsedComposition(source, "AtomicHasOut") {
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
                Set("AtomicHasOut"),
                Some(currentAnnotation),
                order
              )
              val outcome =
                if annotationName == applyName then new ApplyHandler().expand(input)
                else
                  InstanceHandler.expandWithLowering(input): (_, _) =>
                    Left(
                      InstanceFactoryPeerBridge.Failure(
                        "PROMPT100_CONTROLLED_LOWERING_FAILURE",
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
              "PROMPT100_CONTROLLED_LOWERING_FAILURE: no partial apply or instance edit may survive"
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
          assertEquals(
            output.companion.toList.flatMap(_.impl.body).collect {
              case method: DefDef if Set("apply", "instance").contains(method.name.toString) =>
                method.name.toString
            },
            Nil,
            clue(order)
          )
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
       |  type Out
       |  type Item = A
       |  def identity(value: A): A = value
       |  type Copy = Out
       |  type Value = Copy
       |  def pick(first: A, second: A): A = first
       |  type Branch = Item
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
        s"""def instance[A, Out0]: $className[A] { type Out = Out0 } =
           |    new $className[A]:
           |      type Out = Out0""".stripMargin
      else ""
    s"""@current
       |trait $className[A]:
       |  type Out
       |  type Copy = Out
       |  def identity(value: Copy): Out = value
       |
       |object $className:
       |  val retained = 1
       |  $applyDefinition
       |  $instanceDefinition
       |""".stripMargin

  private def shortName(annotationName: String): String =
    if annotationName == applyName then "apply" else "instance"
