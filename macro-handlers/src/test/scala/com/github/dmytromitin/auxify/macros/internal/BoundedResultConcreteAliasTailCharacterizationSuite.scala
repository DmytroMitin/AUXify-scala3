package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.ExpansionOutcome
import quasiquotes.definitions.dotty.AuxTypeAliasPeerBridge

class BoundedResultConcreteAliasTailCharacterizationSuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val auxName = "com.github.dmytromitin.auxify.macros.aux"

  private val routes = List(
    "standalone aux" -> List(auxName),
    "full apply" -> List(applyName),
    "apply then aux" -> List(applyName, auxName),
    "aux then apply" -> List(auxName, applyName)
  )

  routes.foreach: (label, order) =>
    test(s"$label preserves the accepted one-result no-tail source") {
      val (output, context) = expandWithContext(
        """trait Add[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |  def apply(n: N, m: M): Out
          |""".stripMargin,
        order
      )
      given Context = context

      assertEquals(output.primary.name.toString, "Add")
    }

    test(s"$label admits direct result aliases with enclosing-parameter aliases") {
      val (output, context) = expandWithContext(
        """trait RichAdd[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |  type Copy = Out
          |  type Left = N
          |  def apply(n: N, m: M): Out
          |  type OtherCopy = Out
          |  type Right = M
          |""".stripMargin,
        order
      )
      given Context = context

      assertEquals(output.primary.name.toString, "RichAdd")
    }

  test("both composition orders keep aliases inherited and generate one result refinement each") {
    List(List(applyName, auxName), List(auxName, applyName)).foreach: order =>
      val (output, context) = expandWithContext(
        """trait Fresh[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |  type Out0 = Out
          |  def apply(n: N, m: M): Out
          |  type Out1 = N
          |  type Copy = Out
          |  type Right = M
          |""".stripMargin,
        order
      )
      given Context = context
      val members = output.companion.toList.flatMap(_.impl.body)
      val aliases = members.collect {
        case value: TypeDef if value.name.toString == "Aux" => value
      }
      val methods = members.collect {
        case value: DefDef if value.name.toString == "apply" => value
      }
      assertEquals(aliases.size, 1, clue(order))
      assertEquals(methods.size, 1, clue(order))

      aliases.head.rhs match
        case LambdaTypeTree(parameters, RefinedTypeTree(_, List(result: TypeDef))) =>
          assertEquals(parameters.map(_.name.toString), List("N", "M", "Out2"), clue(order))
          assertEquals(result.name.toString, "Out", clue(order))
        case other => fail(s"expected one-result Aux topology for $order, found $other")
      methods.head.tpt match
        case RefinedTypeTree(_, List(result: TypeDef)) =>
          assertEquals(result.name.toString, "Out", clue(order))
        case other => fail(s"expected one-result apply topology for $order, found $other")

      assertEquals(
        aliases.head.source.content.mkString,
        "type Aux[N <: Nat, M <: Nat, Out2 <: Nat] = Fresh[N, M] { type Out = Out2 }",
        clue(order)
      )
      assertEquals(
        methods.head.source.content.mkString,
        "def apply[N <: Nat, M <: Nat](using inst: Fresh[N, M]): Fresh[N, M] { type Out = inst.Out } = inst",
        clue(order)
      )
  }

  test("both composition orders independently preserve existing Aux and apply members") {
    List(List(applyName, auxName), List(auxName, applyName)).foreach: order =>
      val (output, context) = expandWithContext(
        """trait Existing[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |  type Copy = Out
          |  type Left = N
          |  type Right = M
          |
          |object Existing:
          |  type Aux = String
          |  def apply = 42
          |  val preserved = 84
          |""".stripMargin,
        order
      )
      given Context = context
      val members = output.companion.getOrElse(fail("missing companion")).impl.body
      assertEquals(
        members.collect { case value: TypeDef if value.name.toString == "Aux" => value }.size,
        1,
        clue(order)
      )
      assertEquals(
        members.collect { case value: DefDef if value.name.toString == "apply" => value }.size,
        1,
        clue(order)
      )
      assert(
        members.exists {
          case value: ValDef => value.name.toString == "preserved"
          case _ => false
        },
        clue(order)
      )
  }

  test("a reachable Aux lowering failure rolls back an earlier staged apply in either order") {
    List(List(applyName, auxName), List(auxName, applyName)).foreach: order =>
      val source =
        """@current
          |@current
          |trait AtomicRichAdd[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |  type Copy = Out
          |  type Left = N
          |  type Right = M
          |
          |object AtomicRichAdd:
          |  val retained = 17
          |""".stripMargin
      val unit = CompilationUnit("AtomicRichAdd.scala", source)
      given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
      val stats = new Parsers.Parser(unit.source).parse() match
        case PackageDef(_, values) => values
        case other => fail(s"missing package stats in $other")
      val primary = stats.collectFirst { case value: TypeDef => value }
        .getOrElse(fail("missing AtomicRichAdd trait"))
      val companion = stats.collectFirst { case value: ModuleDef => value }
      val original = StructuredExpansionOutput(primary, companion)
      val originalBody = companion.getOrElse(fail("missing companion")).impl.body
      val currentAnnotation = Trees.mods(primary).annotations.head
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
            Set("AtomicRichAdd"),
            Some(currentAnnotation),
            order
          )
          val outcome =
            if annotationName == applyName then new ApplyHandler().expand(input)
            else
              AuxHandler.expandWithLowering(input): (_, _) =>
                Left(
                  AuxTypeAliasPeerBridge.Failure(
                    "PROMPT103_CONTROLLED_LOWERING_FAILURE",
                    "no partial apply or Aux edit may survive"
                  )
                )
          outcome match
            case ExpansionOutcome.Structured(changes) =>
              staged = StructuredOutcomeTestSupport.materialize(input, changes)
              applyWasStaged = staged.companion.exists(_.impl.body.exists {
                case method: DefDef => method.name.toString == "apply"
                case _ => false
              })
            case ExpansionOutcome.Rejected(values) =>
              diagnostics = values.map(_.message)
            case other => fail(s"expected structured change or rejection, found $other")

      val output = if diagnostics.nonEmpty then original else staged
      assertEquals(
        diagnostics,
        List(
          "PROMPT103_CONTROLLED_LOWERING_FAILURE: no partial apply or Aux edit may survive"
        ),
        clue(order)
      )
      assertEquals(
        visited,
        if order.head == applyName then List(applyName, auxName) else List(auxName),
        clue(order)
      )
      assertEquals(applyWasStaged, order.head == applyName, clue(order))
      assert(output.primary.eq(original.primary), clue(order))
      val rolledBack = output.companion.getOrElse(fail("missing rolled-back companion"))
      val originalCompanion = original.companion.getOrElse(fail("missing original companion"))
      assert(rolledBack.eq(originalCompanion), clue(order))
      assert(rolledBack.impl.body.eq(originalBody), clue(order))
  }

  private def expand(
      body: String,
      order: List[String]
  ): StructuredExpansionOutput = expandWithContext(body, order)._1

  private def expandWithContext(
      body: String,
      order: List[String]
  ): (StructuredExpansionOutput, Context) =
    val annotations = List.fill(order.size)("@current").mkString("\n")
    val source = s"$annotations\n$body"
    val unit = CompilationUnit("BoundedResultConcreteAliasTailFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst {
      case value: ModuleDef if value.name.toString == primary.name.toString => value
    }
    val currentAnnotation = Trees.mods(primary).annotations.head

    val output = order.foldLeft(StructuredExpansionOutput(primary, companion)):
      case (staged, annotationName) =>
        val input = paradise3.api.ExpansionInputTestFactory(
          annotationName,
          staged.primary,
          staged.companion,
          Set(staged.primary.name.toString),
          Some(currentAnnotation),
          order
        )
        val outcome =
          if annotationName == applyName then new ApplyHandler().expand(input)
          else new AuxHandler().expand(input)
        outcome match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport.materialize(input, changes)
          case ExpansionOutcome.Rejected(diagnostics) =>
            fail(diagnostics.map(_.message).mkString("; "))
          case other => fail(s"expected structured expansion for $order, found $other")
    (output, summon[Context])
