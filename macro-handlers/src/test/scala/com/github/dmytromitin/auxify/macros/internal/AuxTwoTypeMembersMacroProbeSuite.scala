package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{AnnotatedClassTypeStructureView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.{DirectTypeShape, DirectVisibility}
import paradise3.api.AnnotatedClassTypeStructureView.{
  Bound,
  DirectTypeMember,
  DirectTypeMemberKind
}

class AuxTwoTypeMembersMacroProbeSuite extends munit.FunSuite:
  test("normalized type-structure view exposes the complete ordered two-member aux shape") {
    List(
      ("BiAux", "N", "M", "Out", "Carry", "Nat"),
      ("BiEvidence", "Left", "Right", "Result", "Remainder", "Domain")
    ).foreach: (traitName, firstName, secondName, firstMember, secondMember, boundName) =>
      val view = decode(
        s"""trait $traitName[$firstName <: $boundName, $secondName <: $boundName]:
           |  type $firstMember <: $boundName
           |  def combine(left: $firstName, right: $secondName): $firstMember
           |  type $secondMember <: $boundName
           |""".stripMargin,
        traitName
      )

      assertEquals(view.typeParameters.map(_.name), List(firstName, secondName))
      view.typeParameters.foreach: parameter =>
        assertEquals(parameter.variance, AnnotatedClassView.Variance.Invariant)
        assertEquals(parameter.lowerBound, Bound.Absent)
        assertNamedBound(parameter.upperBound, boundName)
        assert(!parameter.hasContextBounds)
        assert(parameter.pos.span.exists)

      assertEquals(view.directTypeMembers.map(_.name), List(firstMember, secondMember))
      assertEquals(view.directTypeMembers.map(_.bodyIndex), List(0, 2))
      assert(view.directTypeMembers.head.pos.span.start < view.directTypeMembers(1).pos.span.start)
      view.directTypeMembers.foreach: member =>
        assertAdmittedMember(member, boundName)
  }

  test("normalized type-structure view distinguishes every adjacent excluded member shape") {
    val alias = member("type Out = Nat")
    assertEquals(alias.kind, DirectTypeMemberKind.Alias)
    assertEquals(alias.lowerBound, Bound.Absent)
    assertEquals(alias.upperBound, Bound.Absent)
    alias.aliasTarget match
      case Some(DirectTypeShape.NamedType(name, pos)) =>
        assertEquals(name, "Nat")
        assert(pos.span.exists)
      case other => fail(s"expected named alias target, found $other")

    val lowerBounded = member("type Out >: Nothing <: Nat")
    assertNamedBound(lowerBounded.lowerBound, "Nothing")
    assertNamedBound(lowerBounded.upperBound, "Nat")

    val polymorphic = member("type Out[A] <: Nat")
    assertEquals(polymorphic.typeParameters.map(_.name), List("A"))
    assert(polymorphic.typeParameters.head.pos.span.exists)

    val protectedMember = member("protected type Out <: Nat")
    assertEquals(protectedMember.modifiers.visibility, DirectVisibility.Protected)
    assertEquals(protectedMember.modifiers.unsupportedFlags, List("protected"))

    val annotated = member("@deprecated type Out <: Nat")
    assert(annotated.modifiers.hasAnnotations)
    assertEquals(annotated.modifiers.annotationCount, 1)

    val overridden = member("override type Out <: Nat")
    assertEquals(overridden.modifiers.unsupportedFlags, List("override"))

    val wrongBound = member("type Out <: Other")
    assertNamedBound(wrongBound.upperBound, "Other")

    val threeMembers = decode(
      """trait BiAux[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Carry <: Nat
        |  type Extra <: Nat
        |""".stripMargin,
      "BiAux"
    )
    assertEquals(threeMembers.directTypeMembers.map(_.name), List("Out", "Carry", "Extra"))
    assertEquals(threeMembers.directTypeMembers.map(_.bodyIndex), List(0, 1, 2))
  }

  private def assertAdmittedMember(member: DirectTypeMember, boundName: String): Unit =
    assertEquals(member.kind, DirectTypeMemberKind.AbstractBounds)
    assertEquals(member.typeParameters, Nil)
    assertEquals(member.lowerBound, Bound.Absent)
    assertNamedBound(member.upperBound, boundName)
    assertEquals(member.aliasTarget, None)
    assertEquals(member.modifiers.visibility, DirectVisibility.Public)
    assert(!member.modifiers.hasAnnotations)
    assertEquals(member.modifiers.annotationCount, 0)
    assertEquals(member.modifiers.unsupportedFlags, Nil)
    assert(member.pos.span.exists)

  private def assertNamedBound(bound: Bound, expectedName: String): Unit =
    bound match
      case Bound.Present(DirectTypeShape.NamedType(name, pos)) =>
        assertEquals(name, expectedName)
        assert(pos.span.exists)
      case other => fail(s"expected named bound `$expectedName`, found $other")

  private def member(memberSource: String): DirectTypeMember =
    decode(
      s"trait BiAux[N <: Nat, M <: Nat]:\n  $memberSource\n  type Carry <: Nat\n",
      "BiAux"
    ).directTypeMembers.head

  private def decode(
      source: String,
      traitName: String
  ): AnnotatedClassTypeStructureView =
    val unit = CompilationUnit(s"${traitName}TwoMemberAuxProbe.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    AnnotatedClassTypeStructureView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
