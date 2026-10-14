package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.*
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionHandler, ExpansionInput, ExpansionOutcome}

import quasiquotes.definitions.dotty.ExtensionModulePeerBridge

class SyntaxHandlerSuite extends munit.FunSuite:
  test("implements the current protocol with the public syntax marker") {
    val handler: ExpansionHandler = new SyntaxHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.syntax"
    )
  }

  test("decodes lowers and places the canonical native extension module") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Monoid"
    ) { (input, _, _, context) =>
      given Context = context

      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val generated = output.companion
            .toList
            .flatMap(_.impl.body)
            .collectFirst {
              case module: ModuleDef if module.name == termName("syntax") => module
            }
            .getOrElse(fail("missing generated syntax module"))

          assertEquals(generated.name.toString, "syntax")
          assertEquals(generated.impl.body.size, 1)
        case other => fail(s"expected structured syntax expansion, found $other")
    }
  }
  test("requests normalized type structure for an inherited alias tail") {
    withExpansionInput(
      """@current
        |trait AliasMonoid[A]:
        |  def combine(left: A, right: A): A
        |  type inst = A
        |""".stripMargin,
      "AliasMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val generated = output.companion.toList.flatMap(_.impl.body).collect {
            case module: ModuleDef if module.name == termName("syntax") => module
          }
          assertEquals(generated.size, 1)
          assertEquals(generated.head.impl.body.size, 1)
        case other => fail(s"expected alias-tail syntax expansion, found $other")
    }
  }

  test("preserves unrelated companion members in order before generated syntax") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
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
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body

      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          assert(
            merged.impl.body.take(originalBody.size).zip(originalBody).forall(_ eq _),
            clue(merged.impl.body)
          )
          assertEquals(directTermMembersNamed(merged, "syntax").size, 1)
        case other => fail(s"expected structured syntax expansion, found $other")
    }
  }

  test("PreserveExisting retains each direct syntax term conflict without duplication") {
    List(
      "object syntax:\n  val existing = 1",
      "def syntax: Int = 1",
      "val syntax: Int = 1"
    ).foreach { conflict =>
      withExpansionInput(
        s"""@current
           |trait Monoid[A]:
           |  def combine(a: A, a1: A): A
           |
           |object Monoid:
           |${indent(conflict)}
           |""".stripMargin,
        "Monoid"
      ) { (input, _, companion, context) =>
        given Context = context
        val original = companion.getOrElse(fail("missing fixture companion"))
        new SyntaxHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            val output = StructuredOutcomeTestSupport.materialize(input, changes)
            val preserved = output.companion.getOrElse(fail("missing companion"))
            assert(preserved.eq(original), clue(conflict))
            assertEquals(directTermMembersNamed(preserved, "syntax").size, 1)
          case other => fail(s"expected structured syntax expansion, found $other")
      }
    }
  }

  test("type syntax and nested syntax definitions are not direct term conflicts") {
    List(
      "type syntax = String",
      "object Nested:\n  object syntax:\n    val existing = 1"
    ).foreach { nonConflict =>
      withExpansionInput(
        s"""@current
           |trait Monoid[A]:
           |  def combine(a: A, a1: A): A
           |
           |object Monoid:
           |${indent(nonConflict)}
           |""".stripMargin,
        "Monoid"
      ) { (input, _, _, context) =>
        given Context = context
        new SyntaxHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            val output = StructuredOutcomeTestSupport.materialize(input, changes)
            val merged = output.companion.getOrElse(fail("missing companion"))
            assertEquals(directTermMembersNamed(merged, "syntax").size, 1)
          case other => fail(s"expected structured syntax expansion, found $other")
      }
    }
  }

  test("a heterogeneous tail keeps one extension group and one forwarding method only") {
    withExpansionInput(
      """@current
        |trait RichMonoid[A]:
        |  def combine(left: A, right: A): A
        |  type inst = A
        |  def inst(inst1: A): A = inst1
        |  type Value = A
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "RichMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val output = new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          StructuredOutcomeTestSupport.materialize(input, changes)
        case other => fail(s"expected heterogeneous-tail syntax expansion, found $other")

      val generated = output.companion.toList.flatMap(_.impl.body).collect {
        case module: ModuleDef if module.name == termName("syntax") => module
      }
      assertEquals(generated.size, 1)
      generated.head.impl.body match
        case List(ExtMethods(_, List(method: DefDef))) =>
          assertEquals(method.name.toString, "combine")
          assertEquals(
            method.trailingParamss.map(_.map(_.name.toString)),
            List(List("right"), List("inst2"))
          )
        case other => fail(s"expected one extension group with one method, found $other")
    }
  }

  test("a late invalid inherited member rejects without a partial companion edit") {
    withExpansionInput(
      """@current
        |trait LateInvalidSyntax[A]:
        |  def combine(left: A, right: A): A
        |  type Item = A
        |  def twice(value: A): A = combine(value, value)
        |  type Value = A
        |  val invalid: A = ???
        |
        |object LateInvalidSyntax:
        |  val retained = 17
        |""".stripMargin,
      "LateInvalidSyntax"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalBody = companion.getOrElse(fail("missing companion")).impl.body
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @syntax source shape for `LateInvalidSyntax`: direct body member at index 4 must be a method; found val"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(companion.getOrElse(fail("missing companion")).impl.body.eq(originalBody))
        case other => fail(s"expected controlled late-tail rejection, found $other")
    }
  }
  test("rejects inherited tails when syntax is stacked with delegated") {
    withExpansionInput(
      """@current
        |trait StackedSyntax[A]:
        |  def combine(left: A, right: A): A
        |  type Item = A
        |  def twice(value: A): A = combine(value, value)
        |
        |object StackedSyntax:
        |  val retained = 19
        |""".stripMargin,
      "StackedSyntax",
      List(
        "com.github.dmytromitin.auxify.macros.delegated",
        "com.github.dmytromitin.auxify.macros.syntax"
      )
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalBody = companion.getOrElse(fail("missing companion")).impl.body
      new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @syntax composition shape for `StackedSyntax`: inherited concrete tails require exactly the @apply and @syntax participant cohort"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(companion.getOrElse(fail("missing companion")).impl.body.eq(originalBody))
        case other => fail(s"expected controlled stacked-tail rejection, found $other")
    }
  }
  test("bridge failure preserves code and detail and performs no placement") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |  def twice(value: A): A = combine(value, value)
        |
        |object Monoid:
        |  val retained = 42
        |""".stripMargin,
      "Monoid"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalPrimary = primary.rhs
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body

      SyntaxHandler.expandWithLowering(input): (_, _, _) =>
        Left(
          ExtensionModulePeerBridge.Failure(
            "UNSUPPORTED_EXTENSION_MODULE_TOPOLOGY",
            "controlled malformed definition"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "UNSUPPORTED_EXTENSION_MODULE_TOPOLOGY: controlled malformed definition"
            )
          )
          assertEquals(diagnostics.map(_.pos), List(input.currentAnnotation.sourcePos))
          assert(primary.rhs.eq(originalPrimary), clue(primary.rhs))
          assert(original.impl.body.eq(originalBody), clue(original.impl.body))
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String,
      sourceOrderedHandledAnnotationNames: List[String] = Nil
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}SyntaxHandlerFixture.scala", source)
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

    val input =
      paradise3.api.ExpansionInputTestFactory(
        "com.github.dmytromitin.auxify.macros.syntax",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation),
        sourceOrderedHandledAnnotationNames
      )
    run(
      input,
      primary,
      companion,
      summon[Context]
    )

  private def directTermMembersNamed(
      companion: ModuleDef,
      name: String
  )(using Context): List[MemberDef] =
    companion.impl.body.collect {
      case member: MemberDef if member.name == termName(name) => member
    }

  private def indent(value: String): String =
    value.linesIterator.map(line => s"  $line").mkString("\n")
