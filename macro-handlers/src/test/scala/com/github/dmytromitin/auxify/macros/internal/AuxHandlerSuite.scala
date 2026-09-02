package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionCompositionPolicy,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  StructuredExpansionOutput
}

import quasiquotes.definitions.dotty.AuxTypeAliasPeerBridge

class AuxHandlerSuite extends munit.FunSuite:
  test("claims only the public aux marker and exact bounded composition envelope") {
    val handler = new AuxHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.aux"
    )
    assertEquals(
      handler.targetProfile,
      ExpansionTargetProfile.TwoUpperBoundedGenericTrait
    )
    assertEquals(
      handler.compositionPolicy,
      ExpansionCompositionPolicy.SourceOrdered
    )
    assert(handler.consumesExistingCompanion)
  }

  test("decodes lowers and places the canonical Aux alias") {
    withExpansionInput(
      """@current
        |trait Add[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  def apply(n: N, m: M): Out
        |""".stripMargin,
      "Add"
    ) { (input, _, _, context) =>
      given Context = context
      val alias = new AuxHandler().expand(input) match
        case ExpansionOutcome.Structured(output) => generatedAux(output)
        case other => fail(s"expected structured aux expansion, found $other")

      assertEquals(alias.name.toString, "Aux")
      alias.rhs match
        case LambdaTypeTree(parameters, RefinedTypeTree(_, List(result: TypeDef))) =>
          assertEquals(parameters.map(_.name.toString), List("N", "M", "Out0"))
          assertEquals(result.name.toString, "Out")
        case other => fail(s"expected generated Aux alias, found $other")
    }
  }

  test("preserves unrelated companion members before the generated alias") {
    withExpansionInput(
      """@current
        |trait Combine[Left <: Natural, Right <: Natural]:
        |  type Result <: Natural
        |
        |object Combine:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Combine"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new AuxHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          assert(
            merged.impl.body.take(originalBody.size).zip(originalBody).forall(_ eq _),
            clue(merged.impl.body)
          )
          assertEquals(generatedAux(output).name.toString, "Aux")
        case other => fail(s"expected structured aux expansion, found $other")
    }
  }

  test("PreserveExisting retains one direct existing Aux type unchanged") {
    withExpansionInput(
      """@current
        |trait Existing[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |
        |object Existing:
        |  type Aux = String
        |  val retained = 7
        |""".stripMargin,
      "Existing"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new AuxHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val preserved = output.companion.getOrElse(fail("missing companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(directTypesNamed(preserved, "Aux").size, 1)
        case other => fail(s"expected structured aux expansion, found $other")
    }
  }

  test("a same-spelling term Aux remains outside the type conflict namespace") {
    withExpansionInput(
      """@current
        |trait TermNamespace[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |
        |object TermNamespace:
        |  val Aux = 9
        |""".stripMargin,
      "TermNamespace"
    ) { (input, _, _, context) =>
      given Context = context
      new AuxHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val merged = output.companion.getOrElse(fail("missing companion"))
          assertEquals(directTypesNamed(merged, "Aux").size, 1)
          assert(
            merged.impl.body.exists {
              case value: ValDef => value.name.toString == "Aux"
              case _ => false
            },
            clue(merged.impl.body)
          )
        case other => fail(s"expected structured aux expansion, found $other")
    }
  }

  test("decoder rejection is atomic for the primary and existing companion") {
    withExpansionInput(
      """@current
        |trait AliasResult[N <: Nat, M <: Nat]:
        |  type Out = Nat
        |
        |object AliasResult:
        |  val retained = 11
        |""".stripMargin,
      "AliasResult"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing companion"))
      val originalCompanionBody = existing.impl.body
      new AuxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @aux source shape for `AliasResult`: result type member `Out` must be abstract bounds, found alias"
            )
          )
          assert(fallback.eq(primary), clue(fallback))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(existing.impl.body.eq(originalCompanionBody), clue(existing.impl.body))
        case other => fail(s"expected controlled decoder rejection, found $other")
    }
  }

  test("bridge failure preserves its code and is atomic") {
    withExpansionInput(
      """@current
        |trait BridgeFailure[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |
        |object BridgeFailure:
        |  val retained = 13
        |""".stripMargin,
      "BridgeFailure"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing companion"))
      val originalCompanionBody = existing.impl.body

      AuxHandler.expandWithLowering(input): (_, _) =>
        Left(
          AuxTypeAliasPeerBridge.Failure(
            "EXACT_RAW_LOWERING_FAILED",
            "controlled bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_RAW_LOWERING_FAILED: controlled bridge failure")
          )
          assert(fallback.eq(primary), clue(fallback))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(existing.impl.body.eq(originalCompanionBody), clue(existing.impl.body))
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}AuxHandlerFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst { case value: ModuleDef => value }
    val currentAnnotation = Trees.mods(primary).annotations.head
    run(
      ExpansionInput(
        "com.github.dmytromitin.auxify.macros.aux",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation)
      ),
      primary,
      companion,
      summon[Context]
    )

  private def generatedAux(
      output: StructuredExpansionOutput
  )(using Context): TypeDef =
    output.companion
      .toList
      .flatMap(_.impl.body)
      .collectFirst {
        case member: TypeDef if member.name.toString == "Aux" => member
      }
      .getOrElse(fail("missing generated Aux type"))

  private def directTypesNamed(
      companion: ModuleDef,
      name: String
  )(using Context): List[TypeDef] =
    companion.impl.body.collect {
      case member: TypeDef if member.name.toString == name => member
    }
