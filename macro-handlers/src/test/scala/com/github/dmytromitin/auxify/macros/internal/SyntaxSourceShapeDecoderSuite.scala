package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.meta.*

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.DirectTypeShape

class SyntaxSourceShapeDecoderSuite extends munit.FunSuite:
  private val CanonicalSource =
    """trait Monoid[A]:
      |  def combine(a: A, a1: A): A
      |""".stripMargin

  test("decodes the canonical syntax receiver and forwarding facts") {
    assertEquals(
      decode(CanonicalSource, "Monoid"),
      SyntaxSourceShapeDecoder.SourceShape(
        traitName = "Monoid",
        enclosingTypeParameterName = "A",
        extensionTypeParameterName = "A",
        methodName = "combine",
        receiverParameterName = "a",
        remainingParameterName = "a1",
        evidenceParameterName = "inst"
      )
    )
  }

  test("derives every semantic name from coherently renamed normalized evidence") {
    assertEquals(
      decode(
        """trait Merge[Value]:
          |  def merge(left: Value, right: Value): Value
          |""".stripMargin,
        "Merge"
      ),
      SyntaxSourceShapeDecoder.SourceShape(
        traitName = "Merge",
        enclosingTypeParameterName = "Value",
        extensionTypeParameterName = "Value",
        methodName = "merge",
        receiverParameterName = "left",
        remainingParameterName = "right",
        evidenceParameterName = "inst"
      )
    )
  }

  test("freshens the evidence name past generated-scope term collisions") {
    assertEquals(
      decode(
        """trait Collision[Element]:
          |  def merge(inst: Element, inst1: Element): Element
          |""".stripMargin,
        "Collision"
      ).evidenceParameterName,
      "inst2"
    )
  }

  test("freshens the extension binder when the source type parameter would capture the trait constructor") {
    val decoded = decode(
      """trait Collision[Collision]:
        |  def merge(left: Collision, right: Collision): Collision
        |""".stripMargin,
      "Collision"
    )

    assertEquals(decoded.enclosingTypeParameterName, "Collision")
    assertEquals(decoded.extensionTypeParameterName, "Collision1")
    assertEquals(
      SyntaxDefinitionBuilder.module(decoded).syntax,
      """object syntax {
        |  extension [Collision1](left: Collision1) {
        |    def merge(right: Collision1)(using inst: Collision[Collision1]): Collision1 = inst.merge(left, right)
        |  }
        |}""".stripMargin
    )
  }

  private val rejectedShapes = List(
    (
      "trait name captured by the fixed nested syntax object",
      """trait syntax[A]:
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "syntax",
      "trait name `syntax` conflicts with the fixed generated nested object name `syntax`"
    ),
    (
      "ordinary class target",
      """class NotATrait[A]:
        |  def combine(a: A, a1: A): A = ???
        |""".stripMargin,
      "NotATrait",
      "requires the restricted top-level ordinary trait profile"
    ),
    (
      "sealed trait target",
      """sealed trait SealedMonoid[A]:
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "SealedMonoid",
      "requires the restricted top-level ordinary trait profile"
    ),
    (
      "trait constructor parameter",
      """trait Constructed[A](value: A):
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Constructed",
      "requires the restricted top-level ordinary trait profile"
    ),
    (
      "variant enclosing type parameter",
      """trait Variant[+A]:
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Variant",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "bounded enclosing type parameter",
      """trait Bounded[A <: AnyRef]:
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Bounded",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "concrete method",
      """trait Concrete[A]:
        |  def combine(a: A, a1: A): A = a
        |""".stripMargin,
      "Concrete",
      "direct method `combine` must be abstract"
    ),
    (
      "method-owned type parameter in the bounded first slice",
      """trait Polymorphic[A]:
        |  def combine[B](a: A, a1: A): A
        |""".stripMargin,
      "Polymorphic",
      "direct method `combine` must not declare method type parameters in this first slice; historical Scala 2 @syntax support was broader"
    ),
    (
      "zero value parameters",
      """trait Zero[A]:
        |  def combine(): A
        |""".stripMargin,
      "Zero",
      "direct method `combine` requires exactly two ordinary parameters; found 0"
    ),
    (
      "one value parameter",
      """trait One[A]:
        |  def combine(a: A): A
        |""".stripMargin,
      "One",
      "direct method `combine` requires exactly two ordinary parameters; found 1"
    ),
    (
      "three value parameters",
      """trait Three[A]:
        |  def combine(a: A, a1: A, a2: A): A
        |""".stripMargin,
      "Three",
      "direct method `combine` requires exactly two ordinary parameters; found 3"
    ),
    (
      "multiple parameter clauses",
      """trait Clauses[A]:
        |  def combine(a: A)(a1: A): A
        |""".stripMargin,
      "Clauses",
      "direct method `combine` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "contextual clause",
      """trait Contextual[A]:
        |  def combine(using a: A, a1: A): A
        |""".stripMargin,
      "Contextual",
      "direct method `combine` parameter clause must be ordinary and non-contextual"
    ),
    (
      "defaulted parameter",
      """trait Defaulted[A]:
        |  def combine(a: A = ???, a1: A): A
        |""".stripMargin,
      "Defaulted",
      "direct method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "parameter with another simple type",
      """trait WrongParameter[A]:
        |  def combine(a: Other, a1: A): A
        |""".stripMargin,
      "WrongParameter",
      "direct method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "applied parameter type",
      """trait AppliedParameter[A]:
        |  def combine(a: List[A], a1: A): A
        |""".stripMargin,
      "AppliedParameter",
      "direct method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "qualified parameter type",
      """trait QualifiedParameter[A]:
        |  def combine(a: pkg.A, a1: A): A
        |""".stripMargin,
      "QualifiedParameter",
      "direct method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "function parameter type",
      """trait FunctionParameter[A]:
        |  def combine(a: A => A, a1: A): A
        |""".stripMargin,
      "FunctionParameter",
      "direct method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "result with another simple type",
      """trait WrongResult[A]:
        |  def combine(a: A, a1: A): Other
        |""".stripMargin,
      "WrongResult",
      "direct method `combine` result type must use enclosing type parameter `A`"
    ),
    (
      "applied result type",
      """trait AppliedResult[A]:
        |  def combine(a: A, a1: A): List[A]
        |""".stripMargin,
      "AppliedResult",
      "direct method `combine` result type must use enclosing type parameter `A`"
    ),
    (
      "extra direct body member",
      """trait Extra[A]:
        |  def combine(a: A, a1: A): A
        |  val extra: A
        |""".stripMargin,
      "Extra",
      "requires exactly one direct body member; found 2"
    ),
    (
      "non-method direct body member",
      """trait NonMethod[A]:
        |  val combine: A
        |""".stripMargin,
      "NonMethod",
      "the direct body member must be a method"
    ),
    (
      "protected method",
      """trait ProtectedMethod[A]:
        |  protected def combine(a: A, a1: A): A
        |""".stripMargin,
      "ProtectedMethod",
      "direct method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated method",
      """trait AnnotatedMethod[A]:
        |  @deprecated def combine(a: A, a1: A): A
        |""".stripMargin,
      "AnnotatedMethod",
      "direct method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "unsupported method modifier",
      """trait InlineMethod[A]:
        |  inline def combine(a: A, a1: A): A
        |""".stripMargin,
      "InlineMethod",
      "direct method `combine` must be public, unannotated, and free of unsupported modifiers"
    )
  )

  rejectedShapes.foreach { case (label, source, traitName, reason) =>
    test(s"rejects $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }
  }

  test("rejects normalized val var implicit given and contextual parameter features") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val member = bodyView.members.head
    val method = member.method.getOrElse(fail("missing normalized method"))
    val clause = method.parameterClauses.head
    val first = clause.parameters.head
    val malformedParameters = List(
      first.copy(isVal = true),
      first.copy(isVar = true),
      first.copy(isImplicit = true),
      first.copy(isGiven = true),
      first.copy(isContextual = true)
    )

    malformedParameters.foreach: malformedParameter =>
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      val malformedMethod = method.copy(parameterClauses = malformedClause :: Nil)
      assertRejected(
        SyntaxSourceShapeDecoder.decode(
          classView,
          bodyView.copy(members = member.copy(method = Some(malformedMethod)) :: Nil)
        ),
        "Monoid",
        "direct method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified"
      )
  }

  test("never parses Unsupported.summary as enclosing-type evidence") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val member = bodyView.members.head
    val method = member.method.getOrElse(fail("missing normalized method"))
    val clause = method.parameterClauses.head
    val first = clause.parameters.head

    List("A", "qualified A", "arbitrary decoder text").foreach: summary =>
      val unsupported = DirectTypeShape.Unsupported(
        "unsupported-test-shape",
        summary,
        first.typePos
      )
      val malformedParameter = first.copy(parameterType = unsupported)
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      val malformedMethod = method.copy(parameterClauses = malformedClause :: Nil)
      assertRejected(
        SyntaxSourceShapeDecoder.decode(
          classView,
          bodyView.copy(members = member.copy(method = Some(malformedMethod)) :: Nil)
        ),
        "Monoid",
        "direct method `combine` parameter `a` must use enclosing type parameter `A`"
      )

      val malformedResult = method.copy(resultType = unsupported)
      assertRejected(
        SyntaxSourceShapeDecoder.decode(
          classView,
          bodyView.copy(members = member.copy(method = Some(malformedResult)) :: Nil)
        ),
        "Monoid",
        "direct method `combine` result type must use enclosing type parameter `A`"
      )
  }

  test("uses the normalized type position for a type-shape diagnostic") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val member = bodyView.members.head
    val method = member.method.getOrElse(fail("missing normalized method"))
    val clause = method.parameterClauses.head
    val first = clause.parameters.head
    val malformedParameter = first.copy(
      parameterType = DirectTypeShape.NamedType("Other", first.typePos)
    )
    val malformedClause = clause.copy(
      parameters = malformedParameter :: clause.parameters.tail
    )
    val malformedMethod = method.copy(parameterClauses = malformedClause :: Nil)
    val diagnostic = SyntaxSourceShapeDecoder
      .decode(
        classView,
        bodyView.copy(members = member.copy(method = Some(malformedMethod)) :: Nil)
      )
      .left
      .toOption
      .getOrElse(fail("malformed parameter unexpectedly decoded"))

    assertEquals(diagnostic.pos, first.typePos)
  }

  private def assertRejected(
      decoded: Either[paradise3.api.ExpansionDiagnostic, SyntaxSourceShapeDecoder.SourceShape],
      traitName: String,
      reason: String
  ): Unit =
    val diagnostic = decoded.left.toOption.getOrElse(fail(s"$traitName unexpectedly decoded"))
    assertEquals(
      diagnostic.message,
      s"unsupported @syntax source shape for `$traitName`: $reason"
    )

  private def decode(
      source: String,
      traitName: String
  ): SyntaxSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(source: String, traitName: String) =
    val (classView, bodyView) = decodeViews(source, traitName)
    SyntaxSourceShapeDecoder.decode(classView, bodyView)

  private def decodeViews(
      source: String,
      traitName: String
  ): (AnnotatedClassView, AnnotatedClassBodyView) =
    val unit = CompilationUnit(s"${traitName}SyntaxDecoderFixture.scala", source)
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
