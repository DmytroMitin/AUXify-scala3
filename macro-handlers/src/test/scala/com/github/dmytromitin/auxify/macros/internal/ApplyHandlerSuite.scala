package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  StructuredExpansionOutput
}

class ApplyHandlerSuite extends munit.FunSuite:
  test("the public handler requests admission for both supported apply envelopes") {
    assertEquals(
      new ApplyHandler().targetProfile,
      ExpansionTargetProfile.RestrictedOrTwoUpperBoundedGenericTrait
    )
  }

  test("a two-bounded-parameter trait routes to the refined full apply method") {
    val source =
      """@current
        |trait Add[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  def apply(n: N, m: M): Out
        |""".stripMargin
    withExpansionInput(source, "Add") { (input, primary, context) =>
      given Context = context
      new ApplyHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val generated = generatedApply(output)
          assertEquals(generated.leadingTypeParams.map(_.name.toString), List("N", "M"))
          generated.tpt match
            case RefinedTypeTree(
                  AppliedTypeTree(Ident(base), List(Ident(first), Ident(second))),
                  List(result: TypeDef)
                ) =>
              assertEquals(base.toString, "Add")
              assertEquals(first.toString, "N")
              assertEquals(second.toString, "M")
              assertEquals(result.name.toString, "Out")
            case other => fail(s"expected refined Add[N, M] result, found $other")
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          fail(s"full Add.Out shape was rejected: ${diagnostics.map(_.message)}; fallback=$fallback")
        case other => fail(s"expected structured expansion, found $other")

      assert(primary.rhs ne null)
    }
  }

  test("an unsupported normalized full shape rejects with the original primary and no partial companion") {
    val source =
      """@current
        |trait AliasResult[N <: Nat, M <: Nat]:
        |  type Out = Nat
        |
        |object AliasResult:
        |  val preserved = 91
        |""".stripMargin

    withExpansionInput(source, "AliasResult") { (input, primary, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val companion = input.existingCompanion.getOrElse(fail("missing fixture companion"))
      val originalCompanionBody = companion.impl.body

      new ApplyHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported full @apply source shape for `AliasResult`: result type member `Out` must be abstract bounds, found alias"
            )
          )
          assert(fallback.eq(primary), clue(fallback))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(companion.impl.body.eq(originalCompanionBody), clue(companion.impl.body))
        case other => fail(s"expected controlled rejection, found $other")
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String
  )(run: (ExpansionInput, TypeDef, Context) => A): A =
    val unit = CompilationUnit(s"${className}HandlerFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source).parse()
    val stats = parsed match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst { case value: ModuleDef => value }
    val currentAnnotation = Trees.mods(primary).annotations.head
    run(
      ExpansionInput(
        "com.github.dmytromitin.auxify.macros.apply",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation)
      ), primary, summon[Context]
    )

  private def generatedApply(output: StructuredExpansionOutput)(using Context): DefDef =
    output.companion match
      case Some(companion) =>
        companion.impl.body.collectFirst {
          case method: DefDef if method.name.toString == "apply" => method
        }.getOrElse(fail(s"missing generated apply in ${companion.impl.body}"))
      case None => fail("missing generated companion")
