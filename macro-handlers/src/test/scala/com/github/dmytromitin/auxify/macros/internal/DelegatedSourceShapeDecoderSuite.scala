package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetView}
import paradise3.api.ExpansionTargetBodyView.DirectMemberKind

class DelegatedSourceShapeDecoderSuite extends munit.FunSuite:
  test("decodes the canonical one-method delegated source facts") {
    assertEquals(
      decode(
        """trait Show[A]:
          |  def show(a: A): String
          |""".stripMargin,
        "Show"
      ),
      DelegatedSourceShapeDecoder.SourceShape(
        traitName = "Show",
        typeParameterName = "A",
        methodName = "show",
        variant = DelegatedSourceShapeDecoder.Variant.Unary(
          parameterName = "a",
          resultTypeName = "String"
        )
      )
    )
  }

  test("derives every semantic name from a coherently renamed source") {
    assertEquals(
      decode(
        """trait Render[Element]:
          |  def render(value: Element): Text
          |""".stripMargin,
        "Render"
      ),
      DelegatedSourceShapeDecoder.SourceShape(
        traitName = "Render",
        typeParameterName = "Element",
        methodName = "render",
        variant = DelegatedSourceShapeDecoder.Variant.Unary(
          parameterName = "value",
          resultTypeName = "Text"
        )
      )
    )
  }


  test("decodes a parameterless direct-enclosing-result source as its closed variant") {
    assertEquals(
      decode(
        """trait Empty[A]:
          |  def empty: A
          |""".stripMargin,
        "Empty"
      ),
      DelegatedSourceShapeDecoder.SourceShape(
        traitName = "Empty",
        typeParameterName = "A",
        methodName = "empty",
        variant = DelegatedSourceShapeDecoder.Variant.Parameterless
      )
    )
  }

  private val rejectedShapes = List(
    (
      "explicit empty clause instead of parameterless syntax",
      """trait ExplicitEmpty[A]:
        |  def empty(): A
        |""".stripMargin,
      "ExplicitEmpty",
      "direct method `empty` requires exactly one ordinary parameter; found 0"
    ),
    (
      "one ordinary parameter near the parameterless family",
      """trait OneParameterNearMiss[A]:
        |  def empty(value: A): A
        |""".stripMargin,
      "OneParameterNearMiss",
      "direct method `empty` result type must be one unqualified named type"
    ),
    (
      "applied parameterless result",
      """trait AppliedParameterlessResult[A]:
        |  def empty: List[A]
        |""".stripMargin,
      "AppliedParameterlessResult",
      "parameterless method `empty` result type must use enclosing type parameter `A`"
    ),
    (
      "wrong parameterless named result",
      """trait NamedParameterlessResult[A]:
        |  def empty: String
        |""".stripMargin,
      "NamedParameterlessResult",
      "parameterless method `empty` result type must use enclosing type parameter `A`"
    ),
    (
      "zero enclosing type parameters",
      """trait ZeroOwner:
        |  def empty: Int
        |""".stripMargin,
      "ZeroOwner",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "two enclosing type parameters",
      """trait TwoOwners[A, B]:
        |  def empty: A
        |""".stripMargin,
      "TwoOwners",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "higher-kinded enclosing type parameter",
      """trait HigherKinded[F[_]]:
        |  def empty: F
        |""".stripMargin,
      "HigherKinded",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "context-bounded enclosing type parameter",
      """trait ContextBounded[A: Ordering]:
        |  def empty: A
        |""".stripMargin,
      "ContextBounded",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "private parameterless method",
      """trait PrivateEmpty[A]:
        |  private def empty: A
        |""".stripMargin,
      "PrivateEmpty",
      "direct method `empty` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "missing direct member",
      """trait MissingMember[A]
        |""".stripMargin,
      "MissingMember",
      "requires exactly one direct body member; found 0"
    ),
    (
      "parameterless val instead of method",
      """trait ValMember[A]:
        |  val empty: A
        |""".stripMargin,
      "ValMember",
      "the direct body member must be one method"
    ),
    (
      "parameterless var instead of method",
      """trait VarMember[A]:
        |  var empty: A
        |""".stripMargin,
      "VarMember",
      "the direct body member must be one method"
    ),
    (
      "parameterless type member instead of method",
      """trait TypeMember[A]:
        |  type Empty = A
        |""".stripMargin,
      "TypeMember",
      "the direct body member must be one method"
    ),
    (
      "parameterless nested member instead of method",
      """trait NestedMember[A]:
        |  object Empty
        |""".stripMargin,
      "NestedMember",
      "the direct body member must be one method"
    ),
    (
      "modifier-bearing parameterless method",
      """trait InfixParameterless[A]:
        |  infix def empty: A
        |""".stripMargin,
      "InfixParameterless",
      "direct method `empty` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "concrete method",
      """trait Concrete[A]:
        |  def show(a: A): String = a.toString
        |""".stripMargin,
      "Concrete",
      "direct method `show` must be abstract"
    ),
    (
      "method-owned type parameter",
      """trait Polymorphic[A]:
        |  def show[B](a: A): String
        |""".stripMargin,
      "Polymorphic",
      "direct method `show` must not declare method type parameters"
    ),
    (
      "zero ordinary parameters",
      """trait Zero[A]:
        |  def show(): String
        |""".stripMargin,
      "Zero",
      "direct method `show` requires exactly one ordinary parameter; found 0"
    ),
    (
      "multiple ordinary parameters",
      """trait Multiple[A]:
        |  def show(a: A, b: A): String
        |""".stripMargin,
      "Multiple",
      "direct method `show` requires exactly one ordinary parameter; found 2"
    ),
    (
      "multiple clauses",
      """trait Clauses[A]:
        |  def show(a: A)(b: A): String
        |""".stripMargin,
      "Clauses",
      "direct method `show` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "contextual clause",
      """trait Contextual[A]:
        |  def show(using a: A): String
        |""".stripMargin,
      "Contextual",
      "direct method `show` parameter clause must be ordinary and non-contextual"
    ),
    (
      "default parameter",
      """trait Defaulted[A]:
        |  def show(a: A = ???): String
        |""".stripMargin,
      "Defaulted",
      "direct method `show` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "wrong enclosing parameter reference",
      """trait WrongType[A]:
        |  def show(a: Other): String
        |""".stripMargin,
      "WrongType",
      "direct method `show` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "applied parameter type",
      """trait AppliedParameter[A]:
        |  def show(a: List[A]): String
        |""".stripMargin,
      "AppliedParameter",
      "direct method `show` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "applied result",
      """trait AppliedResult[A]:
        |  def show(a: A): List[String]
        |""".stripMargin,
      "AppliedResult",
      "direct method `show` result type must be one unqualified named type"
    ),
    (
      "qualified result",
      """trait QualifiedResult[A]:
        |  def show(a: A): scala.Predef.String
        |""".stripMargin,
      "QualifiedResult",
      "direct method `show` result type must be one unqualified named type"
    ),
    (
      "function result",
      """trait FunctionResult[A]:
        |  def show(a: A): A => String
        |""".stripMargin,
      "FunctionResult",
      "direct method `show` result type must be one unqualified named type"
    ),
    (
      "extra direct member",
      """trait ExtraMember[A]:
        |  def show(a: A): String
        |  val extra: Int
        |""".stripMargin,
      "ExtraMember",
      "requires exactly one direct body member; found 2"
    ),
    (
      "protected method",
      """trait ProtectedMethod[A]:
        |  protected def show(a: A): String
        |""".stripMargin,
      "ProtectedMethod",
      "direct method `show` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated method",
      """trait AnnotatedMethod[A]:
        |  @deprecated def show(a: A): String
        |""".stripMargin,
      "AnnotatedMethod",
      "direct method `show` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "variant enclosing type parameter",
      """trait Variant[+A]:
        |  def show(a: A): String
        |""".stripMargin,
      "Variant",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "bounded enclosing type parameter",
      """trait Bounded[A <: AnyRef]:
        |  def show(a: A): String
        |""".stripMargin,
      "Bounded",
      "requires exactly one invariant unbounded enclosing type parameter"
    )
  )

  rejectedShapes.foreach { case (label, source, traitName, reason) =>
    test(s"rejects $label") {
      val diagnostic = decodeEither(source, traitName)
        .left
        .toOption
        .getOrElse(fail(s"$traitName unexpectedly decoded"))
      assertEquals(
        diagnostic.message,
        s"unsupported @delegated source shape for `$traitName`: $reason"
      )
    }
  }

  test("rejects malformed normalized direct-member evidence fail closed") {
    val (classView, bodyView) = decodeViews(
      """trait Empty[A]:
        |  def empty: A
        |""".stripMargin,
      "Empty"
    )
    val member = bodyView.members.head
    val method = member.method.getOrElse(fail("missing normalized method"))
    val malformed = List(
      (
        member.copy(kind = DirectMemberKind.Val),
        "the direct body member must be one method"
      ),
      (
        member.copy(method = None),
        "the direct body member must provide normalized method evidence"
      ),
      (
        member.copy(method = Some(method.copy(name = ""))),
        "the direct method must have an available normalized name"
      )
    )

    malformed.foreach: (malformedMember, reason) =>
      val diagnostic = DelegatedSourceShapeDecoder
        .decode("Empty", classView, bodyView.copy(members = malformedMember :: Nil))
        .left
        .toOption
        .getOrElse(fail("malformed normalized direct-member evidence unexpectedly decoded"))
      assertEquals(
        diagnostic.message,
        s"unsupported @delegated source shape for `Empty`: $reason"
      )
  }

  private def decode(
      source: String,
      traitName: String
  ): DelegatedSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeViews(
      source: String,
      traitName: String
  ): (ExpansionTargetView, ExpansionTargetBodyView) =
    val unit = CompilationUnit(s"${traitName}DelegatedDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    decodeViews(unit)

  private def decodeEither(source: String, traitName: String) =
    val unit = CompilationUnit(s"${traitName}DelegatedDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val (classView, bodyView) = decodeViews(unit)
    DelegatedSourceShapeDecoder.decode(traitName, classView, bodyView)

  private def decodeViews(unit: CompilationUnit)(using Context) =
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val classView = ExpansionTargetView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val bodyView = ExpansionTargetBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (classView, bodyView)
