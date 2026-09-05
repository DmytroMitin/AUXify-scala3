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

class InstanceCurriedMethodMacroProbeSuite extends munit.FunSuite:
  test("normalized views expose canonical and renamed curried instance source shapes") {
    List(
      ("Curried", "A", "combine", "a", "b"),
      ("Chain", "Element", "append", "left", "right")
    ).foreach: (traitName, typeName, methodName, firstName, secondName) =>
      val (classView, bodyView) = decode(
        s"trait $traitName[$typeName]:\n  def $methodName($firstName: $typeName)($secondName: $typeName): $typeName\n",
        traitName
      )

      assertEquals(classView.className, traitName)
      assertEquals(classView.definitionKind, AnnotatedClassView.DefinitionKind.Trait)
      assertEquals(classView.constructorClauses, Nil)
      assert(!classView.modifiers.isSealed)
      classView.typeParameters match
        case parameter :: Nil =>
          assertEquals(parameter.name, typeName)
          assertEquals(parameter.variance, AnnotatedClassView.Variance.Invariant)
          assert(parameter.isOrdinaryUnbounded)
          assert(!parameter.hasContextBounds)
          assert(!parameter.isOrdinaryUpperBounded)
          assert(parameter.pos.span.exists)
        case other => fail(s"expected one enclosing type parameter, found $other")

      assertEquals(bodyView.members.size, 1)
      assert(bodyView.pos.span.exists)
      val method = onlyMethod(bodyView)
      assertEquals(method.name, methodName)
      assertEquals(method.status, DirectMethodStatus.Abstract)
      assertEquals(method.typeParameters, Nil)
      assertEquals(method.modifiers.visibility, DirectVisibility.Public)
      assert(!method.modifiers.hasAnnotations)
      assertEquals(method.modifiers.annotationCount, 0)
      assertEquals(method.modifiers.unsupportedFlags, Nil)
      assert(method.pos.span.exists)
      assert(method.resultTypePos.span.exists)
      assertEnclosingType(method.resultType, typeName)
      method.parameterClauses match
        case first :: second :: Nil =>
          assertOrdinaryClause(first, firstName, typeName)
          assertOrdinaryClause(second, secondName, typeName)
          assert(first.pos.span.start < second.pos.span.start)
        case other => fail(s"expected two ordered parameter clauses, found $other")
  }

  test("normalized views keep adjacent excluded curried shapes distinguishable") {
    assertEquals(method("def combine(a: A, b: A): A").parameterClauses.map(_.parameters.size), List(2))
    assertEquals(method("def combine(a: A)(b: A)(c: A): A").parameterClauses.map(_.parameters.size), List(1, 1, 1))

    val contextual = method("def combine(a: A)(using b: A): A")
    assert(contextual.parameterClauses(1).isContextual)
    assert(contextual.parameterClauses(1).isGiven)

    val defaulted = method("def combine(a: A)(b: A = a): A")
    assert(defaulted.parameterClauses(1).parameters.head.hasDefault)

    assertEquals(method("def combine[B](a: A)(b: A): A").typeParameters.map(_.name), List("B"))
    assertEquals(method("def combine(a: A)(b: A): A = a").status, DirectMethodStatus.Concrete)
    assertEquals(method("protected def combine(a: A)(b: A): A").modifiers.visibility, DirectVisibility.Protected)

    method("def combine(a: Other)(b: A): A").parameterClauses.head.parameters.head.parameterType match
      case DirectTypeShape.NamedType(name, _) => assertEquals(name, "Other")
      case other => fail(s"expected distinct wrong parameter Type, found $other")
    method("def combine(a: A)(b: A): Other").resultType match
      case DirectTypeShape.NamedType(name, _) => assertEquals(name, "Other")
      case other => fail(s"expected distinct wrong result Type, found $other")

    val (_, extraBody) = decode(
      "trait Curried[A]:\n  def combine(a: A)(b: A): A\n  val extra: Int\n",
      "Curried"
    )
    assertEquals(extraBody.members.size, 2)
  }

  private def assertOrdinaryClause(
      clause: AnnotatedClassBodyView.DirectMethodParameterClause,
      expectedName: String,
      expectedTypeName: String
  ): Unit =
    assert(!clause.isContextual)
    assert(!clause.isImplicit)
    assert(!clause.isGiven)
    assert(clause.pos.span.exists)
    clause.parameters match
      case parameter :: Nil =>
        assertEquals(parameter.name, expectedName)
        assert(!parameter.isContextual)
        assert(!parameter.isImplicit)
        assert(!parameter.isGiven)
        assert(!parameter.isVal)
        assert(!parameter.isVar)
        assert(!parameter.hasDefault)
        assert(parameter.pos.span.exists)
        assert(parameter.typePos.span.exists)
        assertEnclosingType(parameter.parameterType, expectedTypeName)
      case other => fail(s"expected one ordinary parameter, found $other")

  private def assertEnclosingType(shape: DirectTypeShape, expectedName: String): Unit =
    shape match
      case DirectTypeShape.EnclosingTypeParameter(name, pos) =>
        assertEquals(name, expectedName)
        assert(pos.span.exists)
      case other => fail(s"expected enclosing type-parameter reference, found $other")

  private def method(sourceMethod: String): DirectMethod =
    val (_, bodyView) = decode(s"trait Curried[A]:\n  $sourceMethod\n", "Curried")
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
    val unit = CompilationUnit(s"${traitName}CurriedInstanceProbe.scala", source)
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
