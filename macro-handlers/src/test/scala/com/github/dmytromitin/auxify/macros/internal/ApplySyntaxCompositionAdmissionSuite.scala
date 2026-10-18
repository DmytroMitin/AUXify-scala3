package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.termName
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome}

import quasiquotes.definitions.dotty.ExtensionModulePeerBridge

class ApplySyntaxCompositionAdmissionSuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val syntaxName = "com.github.dmytromitin.auxify.macros.syntax"
  private val delegatedName = "com.github.dmytromitin.auxify.macros.delegated"

  private val admittedOrders = List(
    List(applyName, syntaxName),
    List(syntaxName, applyName)
  )

  test("both exact apply syntax orders admit a decoded inherited tail") {
    admittedOrders.foreach { order =>
      withExpansionInput(validTailSource("RichMonoid"), "RichMonoid", order) {
        (input, _, _, context) =>
          given Context = context
          new SyntaxHandler().expand(input) match
            case ExpansionOutcome.Structured(changes) =>
              val output = StructuredOutcomeTestSupport.materialize(input, changes)
              val generated = output.companion.toList.flatMap(_.impl.body).collect {
                case module: ModuleDef if module.name == termName("syntax") => module
              }
              assertEquals(generated.size, 1, clue(order))
              assertEquals(generated.head.impl.body.size, 1, clue(order))
            case other => fail(s"expected admitted apply-syntax tail for $order, found $other")
      }
    }
  }

  test("an inherited tail rejects any third handled participant") {
    withExpansionInput(
      validTailSource("ThreeParticipants"),
      "ThreeParticipants",
      List(applyName, syntaxName, "com.github.dmytromitin.auxify.macros.instance")
    ) { (input, _, _, context) =>
      given Context = context
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @syntax composition shape for `ThreeParticipants`: inherited concrete tails require exactly the @apply and @syntax participant cohort"
            )
          )
        case other => fail(s"expected exact-cohort rejection, found $other")
    }
  }

  test("delegated plus syntax remains outside inherited-tail composition") {
    withExpansionInput(
      validTailSource("DelegatedSyntax"),
      "DelegatedSyntax",
      List(delegatedName, syntaxName)
    ) { (input, _, _, context) =>
      given Context = context
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @syntax composition shape for `DelegatedSyntax`: inherited concrete tails require exactly the @apply and @syntax participant cohort"
            )
          )
        case other => fail(s"expected delegated-syntax rejection, found $other")
    }
  }

  test("a malformed apply syntax tail keeps the decoder owned diagnostic") {
    withExpansionInput(
      """@current
        |trait InvalidTail[A]:
        |  def combine(left: A, right: A): A
        |  type Item = A
        |  val invalid: A = ???
        |""".stripMargin,
      "InvalidTail",
      List(applyName, syntaxName)
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @syntax source shape for `InvalidTail`: direct body member at index 2 must be a method; found val"
            )
          )
          assert(primary.rhs.eq(originalTemplate))
          assertEquals(companion, None)
        case other => fail(s"expected decoder-owned rejection, found $other")
    }
  }

  test("lowering failure discards staged composition edits in both source orders") {
    admittedOrders.foreach { order =>
      withExpansionInput(
        s"""${validTailSource("RollbackMonoid")}
           |
           |object RollbackMonoid:
           |  val retained = 17
           |""".stripMargin,
        "RollbackMonoid",
        order
      ) { (input, primary, companion, context) =>
        given Context = context
        val originalTemplate = primary.rhs
        val original = companion.getOrElse(fail("missing rollback companion"))
        val originalBody = original.impl.body

        val transaction = runCompositionAtomically(input, primary, companion, order)
        assertEquals(
          transaction.diagnostics,
          List(
            "PROMPT099_CONTROLLED_LOWERING_FAILURE: no partial apply or syntax edit may survive"
          ),
          clue(order)
        )
        assertEquals(
          transaction.visited,
          if order.head == applyName then List(applyName, syntaxName) else List(syntaxName),
          clue(order)
        )
        assertEquals(
          transaction.applyWasStagedBeforeFailure,
          order.head == applyName,
          clue(order)
        )
        assert(transaction.output.primary.rhs.eq(originalTemplate), clue(order))
        val rolledBack = transaction.output.companion.getOrElse(fail("missing rolled-back companion"))
        assert(rolledBack.eq(original), clue(order))
        assert(rolledBack.impl.body.eq(originalBody), clue(order))
        assertEquals(
          rolledBack.impl.body.collect {
            case method: DefDef if method.name == termName("apply") => method
          }.size,
          0,
          clue(order)
        )
        assertEquals(
          rolledBack.impl.body.collect {
            case module: ModuleDef if module.name == termName("syntax") => module
          }.size,
          0,
          clue(order)
        )
      }
    }
  }

  private final case class ControlledTransaction(
      output: StructuredExpansionOutput,
      diagnostics: List[String],
      visited: List[String],
      applyWasStagedBeforeFailure: Boolean
  )

  private def runCompositionAtomically(
      originalInput: ExpansionInput,
      originalPrimary: TypeDef,
      originalCompanion: Option[ModuleDef],
      order: List[String]
  )(using Context): ControlledTransaction =
    val original = StructuredExpansionOutput(originalPrimary, originalCompanion)
    var staged = original
    var diagnostics = List.empty[String]
    var visited = List.empty[String]
    var applyWasStagedBeforeFailure = false

    order.foreach: annotationName =>
      if diagnostics.isEmpty then
        visited = visited :+ annotationName
        val input = paradise3.api.ExpansionInputTestFactory(
          annotationName,
          staged.primary,
          staged.companion,
          Set(staged.primary.name.toString),
          Some(originalInput.currentAnnotation),
          order
        )
        val outcome =
          if annotationName == applyName then new ApplyHandler().expand(input)
          else
            SyntaxHandler.expandWithLowering(input): (_, _, _) =>
              Left(
                ExtensionModulePeerBridge.Failure(
                  "PROMPT099_CONTROLLED_LOWERING_FAILURE",
                  "no partial apply or syntax edit may survive"
                )
              )
        outcome match
          case ExpansionOutcome.Structured(changes) =>
            staged = StructuredOutcomeTestSupport.materialize(input, changes)
            if annotationName == applyName then
              applyWasStagedBeforeFailure = staged.companion.exists(_.impl.body.exists {
                case method: DefDef => method.name == termName("apply")
                case _              => false
              })
          case ExpansionOutcome.Rejected(values) =>
            diagnostics = values.map(_.message)
          case other => fail(s"expected structured change or rejection, found $other")

    ControlledTransaction(
      if diagnostics.nonEmpty then original else staged,
      diagnostics,
      visited,
      applyWasStagedBeforeFailure
    )

  private def validTailSource(className: String): String =
    s"""@current
       |trait $className[A]:
       |  def combine(left: A, right: A): A
       |  type Item = A
       |  def twice(value: A): A = combine(value, value)
       |  type Copy = Item
       |  type Branch = Item
       |""".stripMargin

  private def withExpansionInput[A](
      source: String,
      className: String,
      sourceOrderedHandledAnnotationNames: List[String]
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}ApplySyntaxFixture.scala", source)
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
    val input = paradise3.api.ExpansionInputTestFactory(
      syntaxName,
      primary,
      companion,
      Set(className),
      Some(currentAnnotation),
      sourceOrderedHandledAnnotationNames
    )
    run(input, primary, companion, summon[Context])
