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

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

class InstanceHandlerSuite extends munit.FunSuite:
  test("claims the public instance annotation and restricted generic trait envelope") {
    val handler = new InstanceHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.instance"
    )
    assertEquals(
      handler.targetProfile,
      ExpansionTargetProfile.RestrictedGenericTraitApply
    )
    assertEquals(
      handler.compositionPolicy,
      ExpansionCompositionPolicy.SourceOrdered
    )
    assert(handler.consumesExistingCompanion, clue(handler))
  }

  test("derives and places the canonical instance factory") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Monoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(output) => generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(method.name.toString, "instance")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
    }
  }

  test("derives coherently renamed source names") {
    withExpansionInput(
      """@current
        |trait Choice[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |""".stripMargin,
      "Choice"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(output) => generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(method.leadingTypeParams.map(_.name.toString), List("Element"))
      method.tpt match
        case AppliedTypeTree(Ident(target), List(Ident(argument))) =>
          assertEquals(target.toString, "Choice")
          assertEquals(argument.toString, "Element")
        case other => fail(s"expected renamed applied target, found $other")
      assert(method.rhs.toString.contains("fallback"), clue(method.rhs))
      assert(method.rhs.toString.contains("select"), clue(method.rhs))
    }
  }

  test("uses decoder-selected collision-free carrier names in the lowered factory") {
    withExpansionInput(
      """@current
        |trait Collision[Element]:
        |  def emptyValue: Element
        |  def merge(combineFunction: Element, right: Element): Element
        |""".stripMargin,
      "Collision"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(output) => generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue1", "combineFunction1")
      )
    }
  }

  test("appends the factory after preserving unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Monoid:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Monoid"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "instance")
        case other => fail(s"expected structured instance expansion, found $other")
    }
  }

  test("PreserveExisting retains an existing direct instance member unchanged") {
    withExpansionInput(
      """@current
        |trait Existing[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Existing:
        |  def instance[A](value: A): Existing[A] = ???
        |  val retained = 7
        |""".stripMargin,
      "Existing"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(output) =>
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "instance" => method
            }.size,
            1
          )
        case other => fail(s"expected structured instance expansion, found $other")
    }
  }

  test("bridge failure becomes one atomic rejection without partial companion mutation") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Monoid:
        |  val retained = 11
        |""".stripMargin,
      "Monoid"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing fixture companion"))
      val originalCompanionBody = existing.impl.body

      InstanceHandler.expandWithLowering(input): (_, _) =>
        Left(
          InstanceFactoryPeerBridge.Failure(
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
    val unit = CompilationUnit(s"${className}InstanceHandlerFixture.scala", source)
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
        "com.github.dmytromitin.auxify.macros.instance",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation)
      ),
      primary,
      companion,
      summon[Context]
    )

  private def generatedInstance(
      output: StructuredExpansionOutput
  )(using Context): DefDef =
    output.companion
      .toList
      .flatMap(_.impl.body)
      .collectFirst {
        case method: DefDef if method.name.toString == "instance" => method
      }
      .getOrElse(fail("missing generated instance method"))
