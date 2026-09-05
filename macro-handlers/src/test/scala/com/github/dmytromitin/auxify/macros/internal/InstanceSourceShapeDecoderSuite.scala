package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.meta.*

import paradise3.api.{AnnotatedClassBodyView, AnnotatedClassView}
import paradise3.api.AnnotatedClassBodyView.DirectTypeShape

class InstanceSourceShapeDecoderSuite extends munit.FunSuite:
  private val CanonicalSource =
    """trait Monoid[A]:
      |  def empty: A
      |  def combine(a: A, a1: A): A
      |""".stripMargin

  test("decodes the canonical ordered instance source shape") {
    assertEquals(
      decode(CanonicalSource, "Monoid"),
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "Monoid",
        enclosingTypeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        binaryFirstParameterName = "a",
        binarySecondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
  }

  test("derives every semantic name from a coherently renamed source") {
    assertEquals(
      decode(
        """trait Choice[Element]:
          |  def fallback: Element
          |  def select(left: Element, right: Element): Element
          |""".stripMargin,
        "Choice"
      ),
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "Choice",
        enclosingTypeParameterName = "Element",
        parameterlessMethodName = "fallback",
        binaryMethodName = "select",
        binaryFirstParameterName = "left",
        binarySecondParameterName = "right",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
  }

  test("freshens both carriers past relevant source-term collisions") {
    val decoded = decode(
      """trait Collision[Element]:
        |  def emptyValue: Element
        |  def merge(combineFunction: Element, right: Element): Element
        |""".stripMargin,
      "Collision"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue1")
    assertEquals(decoded.binaryCarrierName, "combineFunction1")
    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[Element](emptyValue1: => Element, combineFunction1: (Element, Element) => Element): Collision[Element] = new Collision[Element] {
        |  override def emptyValue: Element = emptyValue1
        |  override def merge(combineFunction: Element, right: Element): Element = combineFunction1(combineFunction, right)
        |}""".stripMargin
    )
  }

  private val rejectedShapes = List(
    (
      "variant enclosing type parameter",
      """trait Variant[+A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Variant",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "bounded enclosing type parameter",
      """trait Bounded[A <: AnyRef]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Bounded",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "context-bounded enclosing type parameter",
      """trait ContextBounded[A: Ordering]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "ContextBounded",
      "requires exactly one invariant unbounded enclosing type parameter"
    ),
    (
      "missing direct member",
      """trait Missing[A]:
        |  def empty: A
        |""".stripMargin,
      "Missing",
      "requires exactly two direct body members; found 1"
    ),
    (
      "extra direct member",
      """trait Extra[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  val extra: A
        |""".stripMargin,
      "Extra",
      "requires exactly two direct body members; found 3"
    ),
    (
      "non-method member",
      """trait NonMethod[A]:
        |  val empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "NonMethod",
      "direct body member at index 0 must be a method; found val"
    ),
    (
      "concrete parameterless method",
      """trait Concrete[A]:
        |  def empty: A = ???
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Concrete",
      "direct method `empty` must be abstract"
    ),
    (
      "polymorphic parameterless-role method",
      """trait PolyEmpty[A]:
        |  def empty[B]: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "PolyEmpty",
      "direct method `empty` must not declare method type parameters"
    ),
    (
      "polymorphic binary-role method",
      """trait PolyCombine[A]:
        |  def empty: A
        |  def combine[B](a: A, a1: A): A
        |""".stripMargin,
      "PolyCombine",
      "direct method `combine` must not declare method type parameters"
    ),
    (
      "protected method",
      """trait ProtectedMethod[A]:
        |  protected def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "ProtectedMethod",
      "direct method `empty` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated method",
      """trait AnnotatedMethod[A]:
        |  def empty: A
        |  @deprecated def combine(a: A, a1: A): A
        |""".stripMargin,
      "AnnotatedMethod",
      "direct method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "empty-clause parameterless method",
      """trait EmptyClause[A]:
        |  def empty(): A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "EmptyClause",
      "parameterless method `empty` must declare no parameter clauses; found 1"
    ),
    (
      "wrong parameterless result",
      """trait WrongEmptyResult[A]:
        |  def empty: Other
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "WrongEmptyResult",
      "parameterless method `empty` result type must use enclosing type parameter `A`"
    ),
    (
      "contextual binary clause",
      """trait ContextualClause[A]:
        |  def empty: A
        |  def combine(using a: A, a1: A): A
        |""".stripMargin,
      "ContextualClause",
      "binary method `combine` parameter clause must be ordinary and non-contextual"
    ),
    (
      "multiple binary clauses",
      """trait MultipleClauses[A]:
        |  def empty: A
        |  def combine(a: A)(a1: A): A
        |""".stripMargin,
      "MultipleClauses",
      "binary method `combine` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "wrong binary parameter count",
      """trait WrongArity[A]:
        |  def empty: A
        |  def combine(a: A): A
        |""".stripMargin,
      "WrongArity",
      "binary method `combine` requires exactly two ordinary parameters; found 1"
    ),
    (
      "defaulted binary parameter",
      """trait Defaulted[A]:
        |  def empty: A
        |  def combine(a: A = ???, a1: A): A
        |""".stripMargin,
      "Defaulted",
      "binary method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "wrong binary parameter type",
      """trait WrongParameter[A]:
        |  def empty: A
        |  def combine(a: Other, a1: A): A
        |""".stripMargin,
      "WrongParameter",
      "binary method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "applied binary parameter type",
      """trait AppliedParameter[A]:
        |  def empty: A
        |  def combine(a: List[A], a1: A): A
        |""".stripMargin,
      "AppliedParameter",
      "binary method `combine` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "wrong binary result",
      """trait WrongBinaryResult[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): Other
        |""".stripMargin,
      "WrongBinaryResult",
      "binary method `combine` result type must use enclosing type parameter `A`"
    ),
    (
      "reversed method topology",
      """trait Reversed[A]:
        |  def combine(a: A, a1: A): A
        |  def empty: A
        |""".stripMargin,
      "Reversed",
      "parameterless method `combine` must declare no parameter clauses; found 1"
    )
  )

  rejectedShapes.foreach { case (label, source, traitName, reason) =>
    test(s"rejects $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }
  }

  test("uses normalized method type parameters and rejects the first polymorphic method at its position") {
    val singlePolymorphicRows = List(
      (
        """trait PolyEmpty[A]:
          |  def empty[B]: A
          |  def combine(a: A, a1: A): A
          |""".stripMargin,
        "PolyEmpty",
        0,
        "empty"
      ),
      (
        """trait PolyCombine[A]:
          |  def empty: A
          |  def combine[B](a: A, a1: A): A
          |""".stripMargin,
        "PolyCombine",
        1,
        "combine"
      )
    )

    singlePolymorphicRows.foreach { case (source, traitName, methodIndex, methodName) =>
      val (classView, bodyView) = decodeViews(source, traitName)
      val method = bodyView.members(methodIndex).method.getOrElse(
        fail(s"missing normalized method evidence for $traitName.$methodName")
      )

      assertEquals(method.typeParameters.map(_.name), List("B"))
      val diagnostic = InstanceSourceShapeDecoder
        .decode(classView, bodyView)
        .left
        .toOption
        .getOrElse(fail(s"$traitName unexpectedly decoded"))
      assertEquals(
        diagnostic.message,
        s"unsupported @instance source shape for `$traitName`: direct method `$methodName` must not declare method type parameters"
      )
      assertEquals(diagnostic.pos, method.pos)
    }

    val (classView, bodyView) = decodeViews(
      """trait BothPoly[A]:
        |  def empty[B]: A
        |  def combine[C](a: A, a1: A): A
        |""".stripMargin,
      "BothPoly"
    )
    val methods = bodyView.members.map(
      _.method.getOrElse(fail("missing normalized method evidence for BothPoly"))
    )
    assertEquals(methods.map(_.typeParameters.map(_.name)), List(List("B"), List("C")))
    val diagnostic = InstanceSourceShapeDecoder
      .decode(classView, bodyView)
      .left
      .toOption
      .getOrElse(fail("BothPoly unexpectedly decoded"))
    assertEquals(
      diagnostic.message,
      "unsupported @instance source shape for `BothPoly`: direct method `empty` must not declare method type parameters"
    )
    assertEquals(diagnostic.pos, methods.head.pos)
  }

  test("rejects malformed normalized method evidence") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val malformed = bodyView.copy(
      members = bodyView.members.updated(
        0,
        bodyView.members.head.copy(method = None)
      )
    )

    assertRejected(
      InstanceSourceShapeDecoder.decode(classView, malformed),
      "Monoid",
      "direct body member at index 0 must provide normalized method evidence"
    )
  }

  test("never treats Unsupported.summary as enclosing-type evidence") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val binaryMember = bodyView.members(1)
    val binaryMethod = binaryMember.method.getOrElse(fail("missing normalized binary method"))
    val clause = binaryMethod.parameterClauses.head
    val parameter = clause.parameters.head
    val summaries = List("A", "not A", "arbitrary decoder text")

    summaries.foreach: summary =>
      val malformedParameter = parameter.copy(
        parameterType = DirectTypeShape.Unsupported(
          "unsupported-test-shape",
          summary,
          parameter.typePos
        )
      )
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      val malformedMethod = binaryMethod.copy(
        parameterClauses = malformedClause :: Nil
      )
      val malformedMember = binaryMember.copy(method = Some(malformedMethod))
      val malformedBody = bodyView.copy(
        members = bodyView.members.updated(1, malformedMember)
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedBody),
        "Monoid",
        "binary method `combine` parameter `a` must use enclosing type parameter `A`"
      )
  }

  test("rejects normalized val or var parameter flags") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val binaryMember = bodyView.members(1)
    val binaryMethod = binaryMember.method.getOrElse(fail("missing normalized binary method"))
    val clause = binaryMethod.parameterClauses.head
    val first = clause.parameters.head

    List(
      first.copy(isVal = true),
      first.copy(isVar = true)
    ).foreach: malformedParameter =>
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      val malformedMethod = binaryMethod.copy(
        parameterClauses = malformedClause :: Nil
      )
      val malformedBody = bodyView.copy(
        members = bodyView.members.updated(
          1,
          binaryMember.copy(method = Some(malformedMethod))
        )
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedBody),
        "Monoid",
        "binary method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified"
      )
  }

  test("derives the trait name only from the normalized class view") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")

    assertEquals(
      InstanceSourceShapeDecoder.decode(classView, bodyView).map(_.traitName),
      Right("Monoid")
    )
  }

  test("rejects malformed normalized class and enclosing-parameter names") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val malformedNames: List[String] = List("", "<error>", "<unknown>", null)

    malformedNames.foreach: malformedName =>
      val malformedClass = classView.copy(className = malformedName)
      assertRejected(
        InstanceSourceShapeDecoder.decode(malformedClass, bodyView),
        String.valueOf(malformedName),
        "requires an available normalized trait name"
      )

      val malformedTypeParameter = classView.typeParameters.head.copy(
        name = malformedName
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(
          classView.copy(typeParameters = malformedTypeParameter :: Nil),
          bodyView
        ),
        "Monoid",
        "requires exactly one invariant unbounded enclosing type parameter"
      )
  }

  test("rejects malformed normalized method and ordinary-parameter names") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val binaryMember = bodyView.members(1)
    val binaryMethod = binaryMember.method.getOrElse(fail("missing normalized binary method"))
    val clause = binaryMethod.parameterClauses.head
    val first = clause.parameters.head
    val malformedNames: List[String] = List("", "<error>", "<unknown>", null)

    malformedNames.foreach: malformedName =>
      val malformedMethodBody = bodyView.copy(
        members = bodyView.members.updated(
          1,
          binaryMember.copy(method = Some(binaryMethod.copy(name = malformedName)))
        )
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedMethodBody),
        "Monoid",
        "direct method at index 1 must have an available normalized name"
      )

      val malformedParameter = first.copy(name = malformedName)
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      val malformedParameterBody = bodyView.copy(
        members = bodyView.members.updated(
          1,
          binaryMember.copy(
            method = Some(
              binaryMethod.copy(parameterClauses = malformedClause :: Nil)
            )
          )
        )
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedParameterBody),
        "Monoid",
        s"binary method `combine` parameter `${String.valueOf(malformedName)}` must be ordinary, non-defaulted, and unmodified"
      )
  }

  test("rejects normalized unsupported method and implicit or given parameter flags") {
    val (classView, bodyView) = decodeViews(CanonicalSource, "Monoid")
    val binaryMember = bodyView.members(1)
    val binaryMethod = binaryMember.method.getOrElse(fail("missing normalized binary method"))
    val clause = binaryMethod.parameterClauses.head
    val first = clause.parameters.head

    val unsupportedMethod = binaryMethod.copy(
      modifiers = binaryMethod.modifiers.copy(unsupportedFlags = List("inline"))
    )
    assertRejected(
      InstanceSourceShapeDecoder.decode(
        classView,
        bodyView.copy(
          members = bodyView.members.updated(
            1,
            binaryMember.copy(method = Some(unsupportedMethod))
          )
        )
      ),
      "Monoid",
      "direct method `combine` must be public, unannotated, and free of unsupported modifiers"
    )

    List(
      clause.copy(isImplicit = true),
      clause.copy(isGiven = true)
    ).foreach: malformedClause =>
      assertRejected(
        InstanceSourceShapeDecoder.decode(
          classView,
          bodyView.copy(
            members = bodyView.members.updated(
              1,
              binaryMember.copy(
                method = Some(
                  binaryMethod.copy(parameterClauses = malformedClause :: Nil)
                )
              )
            )
          )
        ),
        "Monoid",
        "binary method `combine` parameter clause must be ordinary and non-contextual"
      )

    List(
      first.copy(isImplicit = true),
      first.copy(isGiven = true)
    ).foreach: malformedParameter =>
      val malformedClause = clause.copy(
        parameters = malformedParameter :: clause.parameters.tail
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(
          classView,
          bodyView.copy(
            members = bodyView.members.updated(
              1,
              binaryMember.copy(
                method = Some(
                  binaryMethod.copy(parameterClauses = malformedClause :: Nil)
                )
              )
            )
          )
        ),
        "Monoid",
        "binary method `combine` parameter `a` must be ordinary, non-defaulted, and unmodified"
      )
  }

  private def assertRejected(
      decoded: Either[paradise3.api.ExpansionDiagnostic, InstanceSourceShapeDecoder.SourceShape],
      traitName: String,
      reason: String
  ): Unit =
    val diagnostic = decoded.left.toOption.getOrElse(fail(s"$traitName unexpectedly decoded"))
    assertEquals(
      diagnostic.message,
      s"unsupported @instance source shape for `$traitName`: $reason"
    )

  private def decode(
      source: String,
      traitName: String
  ): InstanceSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(source: String, traitName: String) =
    val (classView, bodyView) = decodeViews(source, traitName)
    InstanceSourceShapeDecoder.decode(classView, bodyView)

  private def decodeViews(
      source: String,
      traitName: String
  ): (AnnotatedClassView, AnnotatedClassBodyView) =
    val unit = CompilationUnit(s"${traitName}InstanceDecoderFixture.scala", source)
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
