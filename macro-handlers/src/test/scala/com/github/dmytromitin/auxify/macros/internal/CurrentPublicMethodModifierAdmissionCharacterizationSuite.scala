package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.compiletime.testing.typeChecks

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{DirectMethod, DirectMethodStatus, DirectVisibility}

class CurrentPublicMethodModifierAdmissionCharacterizationSuite extends munit.FunSuite:
  test("records compiletime-testing screening for method-modifier candidates") {
    val observed = List(
      typeChecks("""trait T[A] { infix def empty: A; def combine(a: A, a1: A): A }"""),
      typeChecks("""trait T[A] { def empty: A; infix def combine(a: A, a1: A): A }"""),
      typeChecks("""trait T[A] { def empty: A; def combine(a: A, a1: A): A; infix def twice(a: A): A = combine(a, a) }"""),
      typeChecks("""trait T[A] { infix def show(a: A): String }"""),
      typeChecks("""import scala.language.experimental.erasedDefinitions; trait T[A] { erased def empty: A; def combine(a: A, a1: A): A }"""),
      typeChecks("""import scala.language.experimental.erasedDefinitions; trait T[A] { def empty: A; erased def combine(a: A, a1: A): A }"""),
      typeChecks("""import scala.language.experimental.erasedDefinitions; trait T[A] { def empty: A; def combine(a: A, a1: A): A; erased def twice(a: A): A = combine(a, a) }"""),
      typeChecks("""import scala.language.experimental.erasedDefinitions; trait T[A] { erased def show(a: A): String }""")
    )
    val expected =
      if scala.util.Properties.versionNumberString == "3.3.8" then List.fill(8)(true)
      else List(true, true, true, true, false, false, false, false)

    assertEquals(observed, expected)
  }

  test("corrected normalized views expose infix on every accepted method role") {
    val plainInstance = directMethods(
      """trait Plain[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "Plain"
    )
    val infixInstance = directMethods(
      """trait Infix[A]:
        |  infix def empty: A
        |  infix def combine(a: A, a1: A): A
        |  infix def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "Infix"
    )
    val plainDelegated = directMethods(
      """trait PlainShow[A]:
        |  def show(a: A): String
        |""".stripMargin,
      "PlainShow"
    ).head
    val infixDelegated = directMethods(
      """trait InfixShow[A]:
        |  infix def show(a: A): String
        |""".stripMargin,
      "InfixShow"
    ).head

    assert(plainInstance.forall(_.modifiers.unsupportedFlags.isEmpty))
    assert(plainDelegated.modifiers.unsupportedFlags.isEmpty)
    assertEquals(
      infixInstance.map(_.modifiers.unsupportedFlags),
      List.fill(3)(List("infix"))
    )
    assertEquals(infixDelegated.modifiers.unsupportedFlags, List("infix"))
    assertEquals(
      infixInstance.map(_.status),
      List(
        DirectMethodStatus.Abstract,
        DirectMethodStatus.Abstract,
        DirectMethodStatus.Concrete
      )
    )
    assertEquals(infixDelegated.status, DirectMethodStatus.Abstract)
    assertEquals(infixDelegated.modifiers.visibility, DirectVisibility.Public)
    assertEquals(infixDelegated.parameterClauses.map(_.parameters.size), List(1))
    assert(infixDelegated.pos.span.exists)
    assert(infixDelegated.resultTypePos.span.exists)
  }

  test("Scala 3.3.8 corrected views expose erased on every accepted method role") {
    if scala.util.Properties.versionNumberString == "3.3.8" then
      val plain = directMethods(
        """trait Plain[A]:
          |  def empty: A
          |  def combine(a: A, a1: A): A
          |  def twice(a: A): A = combine(a, a)
          |""".stripMargin,
        "Plain"
      )
      val erased = directMethods(
        """trait Erased[A]:
          |  erased def empty: A
          |  erased def combine(a: A, a1: A): A
          |  erased def twice(a: A): A = combine(a, a)
          |""".stripMargin,
        "Erased"
      )

      assert(plain.forall(_.modifiers.unsupportedFlags.isEmpty))
      assertEquals(
        erased.map(_.modifiers.unsupportedFlags),
        List.fill(3)(List("erased"))
      )
      assertEquals(erased.map(_.status), plain.map(_.status))
      assert(erased.forall(_.pos.span.exists))
  }

  private def directMethods(source: String, traitName: String): List[DirectMethod] =
    val unit = CompilationUnit(s"${traitName}ModifierCharacterization.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    AnnotatedClassView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    AnnotatedClassBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
      .members
      .map(_.method.getOrElse(fail("missing direct method evidence")))
