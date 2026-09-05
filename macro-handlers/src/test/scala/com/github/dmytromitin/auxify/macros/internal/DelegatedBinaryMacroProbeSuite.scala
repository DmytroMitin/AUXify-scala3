package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{
  DirectMethod,
  DirectMethodParameter,
  DirectMethodStatus,
  DirectTypeShape,
  DirectVisibility
}

class DelegatedBinaryMacroProbeSuite extends munit.FunSuite:
  test("normalized views expose canonical and renamed binary delegated source shapes") {
    List(
      ("Eq", "A", "eqv", "left", "right", "Boolean"),
      ("Comparable", "Element", "matches", "candidate", "reference", "Decision")
    ).foreach: (traitName, typeName, methodName, firstName, secondName, resultName) =>
      val (classView, bodyView) = decode(
        s"trait $traitName[$typeName]:\n  def $methodName($firstName: $typeName, $secondName: $typeName): $resultName\n",
        traitName
      )

      assertEquals(classView.className, traitName)
      assertEquals(classView.definitionKind, AnnotatedClassView.DefinitionKind.Trait)
      assertEquals(classView.constructorClauses, Nil)
      assert(!classView.modifiers.isSealed)
      classView.typeParameters match
        case List(parameter) =>
          assertEquals(parameter.name, typeName)
          assertEquals(parameter.variance, AnnotatedClassView.Variance.Invariant)
          assert(parameter.isOrdinaryUnbounded)
          assert(!parameter.isOrdinaryUpperBounded)
          assert(!parameter.hasContextBounds)
          assert(parameter.pos.span.exists)
        case other => fail(s"expected one enclosing Type parameter, found $other")

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
      assertNamedType(method.resultType, resultName)
      method.parameterClauses match
        case List(clause) =>
          assert(!clause.isContextual)
          assert(!clause.isImplicit)
          assert(!clause.isGiven)
          assert(clause.pos.span.exists)
          clause.parameters match
            case List(first, second) =>
              assertOrdinaryParameter(first, firstName, typeName)
              assertOrdinaryParameter(second, secondName, typeName)
              assert(first.pos.span.start < second.pos.span.start)
            case other => fail(s"expected two ordered ordinary parameters, found $other")
        case other => fail(s"expected one ordinary clause, found $other")
  }

  test("normalized views distinguish binary delegated adjacent exclusions except erased") {
    assertEquals(method("def eqv(left: A): Boolean").parameterClauses.map(_.parameters.size), List(1))
    assertEquals(method("def eqv(left: A, right: A, extra: A): Boolean").parameterClauses.map(_.parameters.size), List(3))
    assertEquals(method("def eqv: Boolean").parameterClauses, Nil)
    assertEquals(method("def eqv(): Boolean").parameterClauses.map(_.parameters.size), List(0))
    assertEquals(method("def eqv(left: A)(right: A): Boolean").parameterClauses.map(_.parameters.size), List(1, 1))

    val contextual = method("def eqv(left: A)(using right: A): Boolean")
    assert(contextual.parameterClauses(1).isContextual)
    assert(contextual.parameterClauses(1).isGiven)
    val implicitClause = method("def eqv(left: A)(implicit right: A): Boolean")
    assert(implicitClause.parameterClauses(1).isContextual)
    assert(implicitClause.parameterClauses(1).isImplicit)

    val defaulted = method("def eqv(left: A, right: A = left): Boolean")
    assert(defaulted.parameterClauses.head.parameters(1).hasDefault)
    assertNamedType(
      method("def eqv(left: Other, right: A): Boolean")
        .parameterClauses.head.parameters.head.parameterType,
      "Other"
    )
    assertNamedType(
      method("def eqv(left: A, right: Other): Boolean")
        .parameterClauses.head.parameters(1).parameterType,
      "Other"
    )
    assertNamedType(
      method("def eqv(left: A, right: A): Decision").resultType,
      "Decision"
    )
    method("def eqv(left: A, right: A): List[Boolean]").resultType match
      case DirectTypeShape.Unsupported(kind, _, _) => assertEquals(kind, "applied-type")
      case other => fail(s"expected unsupported applied result Type, found $other")

    assertEquals(
      method("def eqv[B](left: A, right: A): Boolean").typeParameters.map(_.name),
      List("B")
    )
    assertEquals(
      method("def eqv(left: A, right: A): Boolean = true").status,
      DirectMethodStatus.Concrete
    )
    assertEquals(
      method("protected def eqv(left: A, right: A): Boolean").modifiers.visibility,
      DirectVisibility.Protected
    )
    assert(method("@deprecated def eqv(left: A, right: A): Boolean").modifiers.hasAnnotations)
    assertEquals(
      method("inline def eqv(left: A, right: A): Boolean").modifiers.unsupportedFlags,
      List("inline")
    )

    val (_, extraBody) = decode(
      "trait Eq[A]:\n  def eqv(left: A, right: A): Boolean\n  val extra: Int\n",
      "Eq"
    )
    assertEquals(extraBody.members.size, 2)

    val ordinary = method("def eqv(left: A, right: A): Boolean")
      .parameterClauses.head.parameters.head
    val (_, erasedBody) = decode(
      """import scala.language.experimental.erasedDefinitions
        |trait Eq[A]:
        |  def eqv(erased left: A, right: A): Boolean
        |""".stripMargin,
      "Eq"
    )
    val erased = onlyMethod(erasedBody).parameterClauses.head.parameters.head
    assertEquals(publicParameterFacts(erased), publicParameterFacts(ordinary))
  }

  private def publicParameterFacts(parameter: DirectMethodParameter): Product =
    (
      parameter.name,
      typeFact(parameter.parameterType),
      parameter.isContextual,
      parameter.isImplicit,
      parameter.isGiven,
      parameter.isVal,
      parameter.isVar,
      parameter.hasDefault
    )

  private def typeFact(shape: DirectTypeShape): String =
    shape match
      case DirectTypeShape.EnclosingTypeParameter(name, _) => s"enclosing:$name"
      case DirectTypeShape.NamedType(name, _) => s"named:$name"
      case DirectTypeShape.Unsupported(kind, summary, _) => s"unsupported:$kind:$summary"

  private def assertOrdinaryParameter(
      parameter: DirectMethodParameter,
      expectedName: String,
      expectedTypeName: String
  ): Unit =
    assertEquals(parameter.name, expectedName)
    assert(!parameter.isContextual)
    assert(!parameter.isImplicit)
    assert(!parameter.isGiven)
    assert(!parameter.isVal)
    assert(!parameter.isVar)
    assert(!parameter.hasDefault)
    assert(parameter.pos.span.exists)
    assert(parameter.typePos.span.exists)
    parameter.parameterType match
      case DirectTypeShape.EnclosingTypeParameter(name, pos) =>
        assertEquals(name, expectedTypeName)
        assert(pos.span.exists)
      case other => fail(s"expected enclosing Type-parameter reference, found $other")

  private def assertNamedType(shape: DirectTypeShape, expectedName: String): Unit =
    shape match
      case DirectTypeShape.NamedType(name, pos) =>
        assertEquals(name, expectedName)
        assert(pos.span.exists)
      case other => fail(s"expected named Type $expectedName, found $other")

  private def method(sourceMethod: String): DirectMethod =
    val (_, bodyView) = decode(s"trait Eq[A]:\n  $sourceMethod\n", "Eq")
    onlyMethod(bodyView)

  private def onlyMethod(bodyView: AnnotatedClassBodyView): DirectMethod =
    bodyView.members match
      case List(member) =>
        member.method.getOrElse(fail(s"expected direct method, found $member"))
      case other => fail(s"expected exactly one direct member, found $other")

  private def decode(
      source: String,
      traitName: String
  ): (AnnotatedClassView, AnnotatedClassBodyView) =
    val unit = CompilationUnit(s"${traitName}BinaryDelegatedProbe.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, stats) =>
        stats.collectFirst {
          case value: TypeDef if value.name.toString == traitName => value
        }.getOrElse(fail(s"missing primary TypeDef $traitName in $stats"))
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val classView = AnnotatedClassView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val bodyView = AnnotatedClassBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    classView -> bodyView
