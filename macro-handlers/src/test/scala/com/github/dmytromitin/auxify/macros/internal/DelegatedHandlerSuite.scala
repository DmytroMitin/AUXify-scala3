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

import quasiquotes.definitions.dotty.DelegatedForwardingMethodPeerBridge

class DelegatedHandlerSuite extends munit.FunSuite:
  test("claims the public delegated annotation and restricted generic trait envelope") {
    val handler = new DelegatedHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.delegated"
    )
    assertEquals(
      handler.targetProfile,
      ExpansionTargetProfile.RestrictedGenericTraitApply
    )
    assertEquals(
      handler.compositionPolicy,
      ExpansionCompositionPolicy.SourceOrdered
    )
    assert(handler.consumesExistingCompanion)
  }

  test("derives and places the canonical forwarding method") {
    withExpansionInput(
      """@current
        |trait Show[A]:
        |  def show(a: A): String
        |""".stripMargin,
      "Show"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(output) => generatedMethod(output, "show")
        case other => fail(s"expected structured delegated expansion, found $other")

      assertEquals(method.name.toString, "show")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(method.trailingParamss.map(_.map(_.name.toString)), List(List("a"), List("inst")))
    }
  }

  test("appends the generated method after preserving unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Render[Element]:
        |  def render(value: Element): Text
        |
        |object Render:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Render"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "render")
        case other => fail(s"expected structured delegated expansion, found $other")
    }
  }

  test("PreserveExisting retains the exact direct same-name companion method") {
    withExpansionInput(
      """@current
        |trait Existing[A]:
        |  def show(a: A): String
        |
        |object Existing:
        |  def show[A](a: A): String = "preserved"
        |  val retained = 7
        |""".stripMargin,
      "Existing"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "show" => method
            }.size,
            1
          )
        case other => fail(s"expected structured delegated expansion, found $other")
    }
  }

  test("unsupported normalized source shape rejects with the original primary and companion") {
    withExpansionInput(
      """@current
        |trait Concrete[A]:
        |  def show(a: A): String = a.toString
        |
        |object Concrete:
        |  val retained = 9
        |""".stripMargin,
      "Concrete"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `Concrete`: direct method `show` must be abstract"
            )
          )
          assert(fallback.eq(primary), clue(fallback))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(
            companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody)
          )
        case other => fail(s"expected controlled rejection, found $other")
    }
  }

  test("bridge failure becomes one atomic rejection without a partial companion edit") {
    withExpansionInput(
      """@current
        |trait Show[A]:
        |  def show(a: A): String
        |
        |object Show:
        |  val retained = 11
        |""".stripMargin,
      "Show"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body

      DelegatedHandler.expandWithLowering(input): (_, _) =>
        Left(
          DelegatedForwardingMethodPeerBridge.Failure(
            "EXACT_FORWARDING_LOWERING_FAILED",
            "controlled bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_FORWARDING_LOWERING_FAILED: controlled bridge failure")
          )
          assert(fallback.eq(primary), clue(fallback))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(
            companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody)
          )
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}DelegatedHandlerFixture.scala", source)
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
        "com.github.dmytromitin.auxify.macros.delegated",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation)
      ),
      primary,
      companion,
      summon[Context]
    )

  private def generatedMethod(
      output: StructuredExpansionOutput,
      methodName: String
  )(using Context): DefDef =
    output.companion
      .toList
      .flatMap(_.impl.body)
      .collectFirst {
        case method: DefDef if method.name.toString == methodName => method
      }
      .getOrElse(fail(s"missing generated $methodName method"))
