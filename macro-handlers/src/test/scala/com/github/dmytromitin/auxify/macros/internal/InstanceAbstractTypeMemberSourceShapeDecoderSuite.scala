package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionTargetView
}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape
import paradise3.api.ExpansionTargetTypeStructureView.DirectTypeMemberKind

class InstanceAbstractTypeMemberSourceShapeDecoderSuite extends munit.FunSuite:
  private val CanonicalSource =
    """trait HasOut[A]:
      |  type Out
      |""".stripMargin

  test("decodes the canonical abstract-type-member family") {
    assertEquals(
      decode(CanonicalSource, "HasOut"),
      InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape(
        traitName = "HasOut",
        enclosingTypeParameterName = "A",
        memberName = "Out",
        generatedTypeParameterName = "Out0"
      )
    )
  }

  test("derives renamed roles and freshens past the enclosing type name") {
    assertEquals(
      decode(
        """trait Container[Element0]:
          |  type Element
          |""".stripMargin,
        "Container"
      ),
      InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape(
        traitName = "Container",
        enclosingTypeParameterName = "Element0",
        memberName = "Element",
        generatedTypeParameterName = "Element1"
      )
    )

    assertEquals(
      decode(
        """trait Out0[A]:
          |  type Out
          |""".stripMargin,
        "Out0"
      ),
      InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape(
        traitName = "Out0",
        enclosingTypeParameterName = "A",
        memberName = "Out",
        generatedTypeParameterName = "Out1"
      )
    )
  }

  private val rejectedSources = List(
    (
      "zero enclosing type parameters",
      """trait Zero:
        |  type Out
        |""".stripMargin,
      "Zero",
      "abstract-type-member family requires exactly one enclosing type parameter; found 0"
    ),
    (
      "two enclosing type parameters",
      """trait Two[A, B]:
        |  type Out
        |""".stripMargin,
      "Two",
      "abstract-type-member family requires exactly one enclosing type parameter; found 2"
    ),
    (
      "variant enclosing type parameter",
      """trait Variant[+A]:
        |  type Out
        |""".stripMargin,
      "Variant",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "bounded enclosing type parameter",
      """trait Bounded[A <: Matchable]:
        |  type Out
        |""".stripMargin,
      "Bounded",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "higher-kinded enclosing type parameter",
      """trait Higher[F[_]]:
        |  type Out
        |""".stripMargin,
      "Higher",
      "enclosing type parameter `F` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "context-bounded enclosing type parameter",
      """trait Contextual[A: Ordering]:
        |  type Out
        |""".stripMargin,
      "Contextual",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "concrete alias",
      """trait Alias[A]:
        |  type Out = A
        |""".stripMargin,
      "Alias",
      "direct type member `Out` must be abstract, not a concrete alias"
    ),
    (
      "lower-bounded abstract member",
      """trait Lower[A]:
        |  type Out >: Nothing
        |""".stripMargin,
      "Lower",
      "abstract type member `Out` must be unbounded"
    ),
    (
      "upper-bounded abstract member",
      """trait Upper[A]:
        |  type Out <: Matchable
        |""".stripMargin,
      "Upper",
      "abstract type member `Out` must be unbounded"
    ),
    (
      "two-sided bounded abstract member",
      """trait TwoSided[A]:
        |  type Out >: Nothing <: Matchable
        |""".stripMargin,
      "TwoSided",
      "abstract type member `Out` must be unbounded"
    ),
    (
      "polymorphic abstract member",
      """trait Polymorphic[A]:
        |  type Out[X]
        |""".stripMargin,
      "Polymorphic",
      "abstract type member `Out` must not declare type parameters"
    ),
    (
      "private abstract member",
      """trait PrivateMember[A]:
        |  private type Out
        |""".stripMargin,
      "PrivateMember",
      "abstract type member `Out` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "protected abstract member",
      """trait ProtectedMember[A]:
        |  protected type Out
        |""".stripMargin,
      "ProtectedMember",
      "abstract type member `Out` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated abstract member",
      """trait AnnotatedMember[A]:
        |  @deprecated type Out
        |""".stripMargin,
      "AnnotatedMember",
      "abstract type member `Out` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "modifier-bearing abstract member",
      """trait InfixMember[A]:
        |  infix type Out
        |""".stripMargin,
      "InfixMember",
      "abstract type member `Out` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "two abstract type members",
      """trait TwoMembers[A]:
        |  type Out
        |  type Other
        |""".stripMargin,
      "TwoMembers",
      "abstract-type-member family requires exactly one direct body member; found 2"
    ),
    (
      "abstract type member plus method",
      """trait TypeThenMethod[A]:
        |  type Out
        |  def value: A
        |""".stripMargin,
      "TypeThenMethod",
      "abstract-type-member family requires exactly one direct body member; found 2"
    ),
    (
      "method plus abstract type member",
      """trait MethodThenType[A]:
        |  def value: A
        |  type Out
        |""".stripMargin,
      "MethodThenType",
      "abstract-type-member family requires exactly one direct body member; found 2"
    ),
    (
      "abstract type member plus concrete alias",
      """trait AbstractAndAlias[A]:
        |  type Out
        |  type Other = A
        |""".stripMargin,
      "AbstractAndAlias",
      "abstract-type-member family requires exactly one direct body member; found 2"
    ),
    (
      "abstract val",
      """trait AbstractVal[A]:
        |  val value: A
        |""".stripMargin,
      "AbstractVal",
      "direct body member at index 0 must be an abstract type member; found val"
    ),
    (
      "abstract var",
      """trait AbstractVar[A]:
        |  var value: A
        |""".stripMargin,
      "AbstractVar",
      "direct body member at index 0 must be an abstract type member; found var"
    ),
    (
      "direct method",
      """trait DirectMethod[A]:
        |  def value: A
        |""".stripMargin,
      "DirectMethod",
      "direct body member at index 0 must be an abstract type member; found method"
    ),
    (
      "nested trait",
      """trait Nested[A]:
        |  trait Inner
        |""".stripMargin,
      "Nested",
      "direct body member at index 0 must be an abstract type member; found nested trait"
    )
  )

  rejectedSources.foreach: (label, source, traitName, reason) =>
    test(s"rejects $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }

  test("rejects missing duplicate and wrong-index normalized member records") {
    val (classView, bodyView, typeView) = decodeAllViews(CanonicalSource, "HasOut")
    val member = typeView.directTypeMembers.head
    List(
      typeView.copy(directTypeMembers = Nil),
      typeView.copy(directTypeMembers = List(member, member)),
      typeView.copy(directTypeMembers = List(member.copy(bodyIndex = 1)))
    ).foreach: malformed =>
      assertRejected(
        InstanceAbstractTypeMemberSourceShapeDecoder.decode(
          classView,
          bodyView,
          malformed
        ),
        "HasOut",
        s"direct body member at index 0 must provide exactly one normalized type-member record at body index 0; found ${malformed.directTypeMembers.size}"
      )
  }

  test("rejects malformed normalized names kind modifiers and alias target") {
    val (classView, bodyView, typeView) = decodeAllViews(CanonicalSource, "HasOut")
    val member = typeView.directTypeMembers.head

    assertRejected(
      InstanceAbstractTypeMemberSourceShapeDecoder.decode(
        classView,
        bodyView,
        typeView.copy(directTypeMembers = List(member.copy(name = "<error>")))
      ),
      "HasOut",
      "abstract type member requires an available normalized name"
    )
    assertRejected(
      InstanceAbstractTypeMemberSourceShapeDecoder.decode(
        classView,
        bodyView,
        typeView.copy(
          directTypeMembers = List(member.copy(kind = DirectTypeMemberKind.Unsupported))
        )
      ),
      "HasOut",
      "direct type member `Out` must be abstract, not a concrete alias"
    )
    assertRejected(
      InstanceAbstractTypeMemberSourceShapeDecoder.decode(
        classView,
        bodyView,
        typeView.copy(
          directTypeMembers = List(
            member.copy(aliasTarget = Some(DirectTypeShape.EnclosingTypeParameter("A", member.pos)))
          )
        )
      ),
      "HasOut",
      "abstract type member `Out` must not provide an alias target"
    )
  }

  private def assertRejected(
      decoded: Either[paradise3.api.ExpansionDiagnostic, InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape],
      traitName: String,
      reason: String
  ): Unit =
    val diagnostic = decoded.left.toOption.getOrElse(fail(s"$traitName unexpectedly decoded"))
    assertEquals(
      diagnostic.message,
      s"unsupported @instance source shape for `$traitName`: $reason"
    )
    assert(diagnostic.pos.span.exists, clues(diagnostic))

  private def decode(
      source: String,
      traitName: String
  ): InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(source: String, traitName: String) =
    val (classView, bodyView, typeView) = decodeAllViews(source, traitName)
    InstanceAbstractTypeMemberSourceShapeDecoder.decode(classView, bodyView, typeView)

  private def decodeAllViews(
      source: String,
      traitName: String
  ): (ExpansionTargetView, ExpansionTargetBodyView, ExpansionTargetTypeStructureView) =
    val unit = CompilationUnit(s"${traitName}AbstractTypeInstance.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = parsePrimary(unit, traitName)
    val classView = ExpansionTargetView.decode(primary).fold(d => fail(d.message), identity)
    val bodyView = ExpansionTargetBodyView.decode(primary).fold(d => fail(d.message), identity)
    val typeView = ExpansionTargetTypeStructureView.decode(primary).fold(d => fail(d.message), identity)
    (classView, bodyView, typeView)

  private def parsePrimary(
      unit: CompilationUnit,
      traitName: String
  )(using Context): TypeDef =
    new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef for $traitName in $other")
