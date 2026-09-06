package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.meta.*

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMemberKind,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}

class InstanceConcreteMethodInheritanceCharacterizationSuite extends munit.FunSuite:
  private trait DerivedMonoid[A]:
    def empty: A
    def combine(a: A, a1: A): A
    def twice(a: A): A = combine(a, a)

  private def existingFactory[A](
      emptyValue: => A,
      combineFunction: (A, A) => A
  ): DerivedMonoid[A] =
    new DerivedMonoid[A]:
      override def empty: A = emptyValue
      override def combine(a: A, a1: A): A =
        combineFunction(a, a1)

  test("normalized views distinguish the selected concrete unary method") {
    val (_, bodyView) = decodeViews(
      """trait DerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "DerivedMonoid"
    )

    assertEquals(
      bodyView.members.map(member => (member.kind, member.method.map(_.status))),
      List(
        (DirectMemberKind.Method, Some(DirectMethodStatus.Abstract)),
        (DirectMemberKind.Method, Some(DirectMethodStatus.Abstract)),
        (DirectMemberKind.Method, Some(DirectMethodStatus.Concrete))
      )
    )

    val concrete = bodyView.members(2).method.getOrElse(fail("missing concrete method evidence"))
    assertEquals(concrete.name, "twice")
    assertEquals(concrete.typeParameters, Nil)
    assertEquals(concrete.modifiers.visibility, DirectVisibility.Public)
    assertEquals(concrete.modifiers.annotationCount, 0)
    assertEquals(concrete.modifiers.hasAnnotations, false)
    assertEquals(concrete.modifiers.unsupportedFlags, Nil)
    assert(concrete.pos.span.exists, clue(concrete.pos))
    assert(concrete.resultTypePos.span.exists, clue(concrete.resultTypePos))
    assertEquals(concrete.parameterClauses.size, 1)

    val clause = concrete.parameterClauses.head
    assertEquals(
      (clause.isContextual, clause.isImplicit, clause.isGiven),
      (false, false, false)
    )
    assertEquals(clause.parameters.size, 1)
    assert(clause.pos.span.exists, clue(clause.pos))

    val parameter = clause.parameters.head
    assertEquals(parameter.name, "a")
    assertEquals(
      (
        parameter.hasDefault,
        parameter.isContextual,
        parameter.isImplicit,
        parameter.isGiven,
        parameter.isVal,
        parameter.isVar
      ),
      (false, false, false, false, false, false)
    )
    assertEquals(
      parameter.parameterType,
      DirectTypeShape.EnclosingTypeParameter("A", parameter.typePos)
    )
    assertEquals(
      concrete.resultType,
      DirectTypeShape.EnclosingTypeParameter("A", concrete.resultTypePos)
    )
  }

  test("the accepted first-slice factory needs no concrete-member authoring") {
    val (classView, bodyView) = decodeViews(
      """trait DerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "DerivedMonoid"
    )
    val shape = InstanceSourceShapeDecoder
      .decode(classView, bodyView)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val definition = InstanceDefinitionBuilder.definition(shape).syntax

    assertEquals(
      definition,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): DerivedMonoid[A] = new DerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
    assert(!definition.contains("twice"), clue(definition))
  }

  test("ordinary trait inheritance dispatches through the generated-style combine override") {
    var combineCalls = 0
    val derived = existingFactory[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(derived.twice(21), 42)
    assertEquals(combineCalls, 1)
  }

  private def decodeViews(
      source: String,
      traitName: String
  ): (AnnotatedClassView, AnnotatedClassBodyView) =
    val unit = CompilationUnit(s"${traitName}ConcreteMethodFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val classView = AnnotatedClassView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val bodyView = AnnotatedClassBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (classView, bodyView)
