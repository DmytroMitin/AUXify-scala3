package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMethod,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}

class DelegatedParameterlessMacroProbeSuite extends munit.FunSuite:
  test("normalized views expose the complete parameterless delegated source shape") {
    val (classView, bodyView) = decode(
      """trait Empty[A]:
        |  def empty: A
        |""".stripMargin,
      "Empty"
    )

    classView.typeParameters match
      case parameter :: Nil =>
        assertEquals(parameter.name, "A")
        assertEquals(parameter.variance, AnnotatedClassView.Variance.Invariant)
        assert(parameter.isOrdinaryUnbounded)
        assert(!parameter.hasContextBounds)
        assert(!parameter.isOrdinaryUpperBounded)
      case other => fail(s"expected one enclosing type parameter, found $other")

    val method = onlyMethod(bodyView)
    assertEquals(method.name, "empty")
    assertEquals(method.status, DirectMethodStatus.Abstract)
    assertEquals(method.typeParameters, Nil)
    assertEquals(method.parameterClauses, Nil)
    assertEquals(method.modifiers.visibility, DirectVisibility.Public)
    assert(!method.modifiers.hasAnnotations)
    assertEquals(method.modifiers.annotationCount, 0)
    assertEquals(method.modifiers.unsupportedFlags, Nil)
    method.resultType match
      case DirectTypeShape.EnclosingTypeParameter(name, _) =>
        assertEquals(name, "A")
      case other => fail(s"expected enclosing type-parameter result, found $other")
  }

  test("normalized views keep every adjacent excluded shape distinguishable") {
    assertEquals(method("def empty(): A").parameterClauses.map(_.parameters.size), List(0))
    assertEquals(method("def empty(value: A): A").parameterClauses.map(_.parameters.size), List(1))

    val contextual = method("def empty(using value: A): A")
    assertEquals(contextual.parameterClauses.size, 1)
    assert(contextual.parameterClauses.head.isContextual)
    assert(contextual.parameterClauses.head.isGiven)

    assertEquals(method("def empty: A = ???").status, DirectMethodStatus.Concrete)
    assertEquals(method("def empty[B]: A").typeParameters.map(_.name), List("B"))
    method("def empty: Other").resultType match
      case DirectTypeShape.NamedType(name, _) => assertEquals(name, "Other")
      case other => fail(s"expected distinct wrong named result, found $other")

    val (_, extraBody) = decode(
      """trait Empty[A]:
        |  def empty: A
        |  val extra: Int
        |""".stripMargin,
      "Empty"
    )
    assertEquals(extraBody.members.size, 2)
  }

  private def method(sourceMethod: String): DirectMethod =
    val (_, bodyView) = decode(s"trait Empty[A]:\n  $sourceMethod\n", "Empty")
    onlyMethod(bodyView)

  private def onlyMethod(bodyView: AnnotatedClassBodyView): DirectMethod =
    bodyView.members match
      case member :: Nil =>
        member.method.getOrElse(fail(s"expected direct method, found $member"))
      case other => fail(s"expected exactly one direct member, found $other")

  private def decode(
      source: String,
      traitName: String
  ): (AnnotatedClassView, AnnotatedClassBodyView) =
    val unit = CompilationUnit(s"${traitName}ParameterlessDelegatedProbe.scala", source)
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
    classView -> bodyView
