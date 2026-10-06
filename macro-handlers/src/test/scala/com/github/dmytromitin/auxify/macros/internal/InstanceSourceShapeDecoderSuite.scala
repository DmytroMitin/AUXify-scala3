package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.meta.*

import paradise3.api.{
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionTargetView
}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape
import paradise3.api.ExpansionTargetTypeStructureView.{
  Bound,
  DirectTypeMemberKind
}

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

  test("admits one third-position concrete unary method without changing the factory shape") {
    val decoded = decode(
      """trait DerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "DerivedMonoid"
    )

    assertEquals(
      decoded,
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "DerivedMonoid",
        enclosingTypeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        binaryFirstParameterName = "a",
        binarySecondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): DerivedMonoid[A] = new DerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("admits one third-position concrete binary method without changing the factory shape") {
    val decoded = decode(
      """trait BinaryDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def combineAgain(a: A, a1: A): A = combine(a, a1)
        |""".stripMargin,
      "BinaryDerivedMonoid"
    )

    assertEquals(
      decoded,
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "BinaryDerivedMonoid",
        enclosingTypeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        binaryFirstParameterName = "a",
        binarySecondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): BinaryDerivedMonoid[A] = new BinaryDerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("admits one third-position concrete ternary method without changing the factory shape") {
    val decoded = decode(
      """trait TernaryDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "TernaryDerivedMonoid"
    )

    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): TernaryDerivedMonoid[A] = new TernaryDerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("admits one third-position concrete five-parameter method without changing the factory shape") {
    val decoded = decode(
      """trait FiveDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: A, b: A, c: A, d: A, e: A): A =
        |    combine(combine(combine(combine(a, b), c), d), e)
        |""".stripMargin,
      "FiveDerivedMonoid"
    )

    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): FiveDerivedMonoid[A] = new FiveDerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("admits one third-position concrete parameterless method without changing the factory shape") {
    val decoded = decode(
      """trait ZeroMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zero: A = empty
        |""".stripMargin,
      "ZeroMonoid"
    )

    assertEquals(
      decoded,
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "ZeroMonoid",
        enclosingTypeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        binaryFirstParameterName = "a",
        binarySecondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): ZeroMonoid[A] = new ZeroMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("derives renamed concrete-method evidence and includes it in carrier freshness") {
    val decoded = decode(
      """trait DerivedChoice[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |  def emptyValue(combineFunction: Element): Element = select(combineFunction, combineFunction)
        |""".stripMargin,
      "DerivedChoice"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue1")
    assertEquals(decoded.binaryCarrierName, "combineFunction1")
    assertEquals(decoded.parameterlessMethodName, "fallback")
    assertEquals(decoded.binaryMethodName, "select")
  }

  test("derives renamed larger-arity concrete-method evidence and freshens past every parameter") {
    val decoded = decode(
      """trait LargerDerivedChoice[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |  def combineFunction(
        |    first: Element,
        |    emptyValue: Element,
        |    second: Element,
        |    emptyValue1: Element,
        |    emptyValue2: Element
        |  ): Element = select(first, emptyValue2)
        |""".stripMargin,
      "LargerDerivedChoice"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue3")
    assertEquals(decoded.binaryCarrierName, "combineFunction1")
    assertEquals(decoded.parameterlessMethodName, "fallback")
    assertEquals(decoded.binaryMethodName, "select")
  }

  test("admits two final inherited concrete methods without changing the factory shape") {
    val decoded = decode(
      """trait TwoDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |  def thrice(a: A): A = combine(twice(a), a)
        |""".stripMargin,
      "TwoDerivedMonoid"
    )

    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): TwoDerivedMonoid[A] = new TwoDerivedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("admits three mixed-arity inherited concrete methods without changing the factory shape") {
    val decoded = decode(
      """trait RichMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zeroLike: A = empty
        |  def twice(a: A): A = combine(a, a)
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "RichMonoid"
    )

    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): RichMonoid[A] = new RichMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("freshens carriers past every inherited method and parameter in a renamed tail") {
    val decoded = decode(
      """trait RichChoice[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |  def emptyValue: Element = fallback
        |  def combineFunction(emptyValue1: Element): Element = select(emptyValue1, emptyValue1)
        |  def fold3(first: Element, emptyValue2: Element, combineFunction1: Element): Element =
        |    select(select(first, emptyValue2), combineFunction1)
        |""".stripMargin,
      "RichChoice"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue3")
    assertEquals(decoded.binaryCarrierName, "combineFunction2")
    assertEquals(decoded.parameterlessMethodName, "fallback")
    assertEquals(decoded.binaryMethodName, "select")
  }

  test("admits a method followed by a concrete alias") {
    val decoded = decode(
      """trait MethodThenAlias[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |  type Item = A
        |""".stripMargin,
      "MethodThenAlias"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue")
    assertEquals(decoded.binaryCarrierName, "combineFunction")
  }

  test("admits a concrete alias followed by a method") {
    val decoded = decode(
      """trait AliasThenMethod[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |  def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "AliasThenMethod"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue")
    assertEquals(decoded.binaryCarrierName, "combineFunction")
  }

  test("admits two concrete aliases") {
    val decoded = decode(
      """trait TwoAliases[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |  type Value = A
        |""".stripMargin,
      "TwoAliases"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue")
    assertEquals(decoded.binaryCarrierName, "combineFunction")
  }

  test("admits multiple methods and aliases interleaved") {
    val decoded = decode(
      """trait RichTypedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |  def twice(a: A): A = combine(a, a)
        |  type Value = A
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "RichTypedMonoid"
    )

    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): RichTypedMonoid[A] = new RichTypedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("type-alias names do not occupy the term-carrier namespace") {
    val decoded = decode(
      """trait NamespaceMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type emptyValue = A
        |  type combineFunction = A
        |  def fold3(emptyValue1: A, combineFunction1: A, c: A): A =
        |    combine(combine(emptyValue1, combineFunction1), c)
        |""".stripMargin,
      "NamespaceMonoid"
    )

    assertEquals(decoded.parameterlessCarrierName, "emptyValue")
    assertEquals(decoded.binaryCarrierName, "combineFunction")
  }

  test("admits coherently renamed aliases, methods, and parameters") {
    val decoded = decode(
      """trait RenamedRichTyped[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |  type Value = Element
        |  def duplicate(value: Element): Element = select(value, value)
        |  type Output = Element
        |  def merge3(first: Element, second: Element, third: Element): Element =
        |    select(select(first, second), third)
        |""".stripMargin,
      "RenamedRichTyped"
    )

    assertEquals(decoded.parameterlessMethodName, "fallback")
    assertEquals(decoded.binaryMethodName, "select")
    assertEquals(decoded.binaryFirstParameterName, "left")
    assertEquals(decoded.binarySecondParameterName, "right")
    assertEquals(decoded.parameterlessCarrierName, "emptyValue")
    assertEquals(decoded.binaryCarrierName, "combineFunction")
  }

  test("admits one third-position concrete alias without changing the factory shape") {
    val decoded = decode(
      """trait WrappedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |""".stripMargin,
      "WrappedMonoid"
    )

    assertEquals(
      decoded,
      InstanceSourceShapeDecoder.SourceShape(
        traitName = "WrappedMonoid",
        enclosingTypeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        binaryFirstParameterName = "a",
        binarySecondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )
    assertEquals(
      InstanceDefinitionBuilder.definition(decoded).syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): WrappedMonoid[A] = new WrappedMonoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
  }

  test("uses normalized third-position alias evidence and rejects infix") {
    List(false, true).foreach: infix =>
      val traitName = if infix then "InfixItem" else "PlainItem"
      val source =
        s"""trait $traitName[A]:
           |  def empty: A
           |  def combine(a: A, a1: A): A
           |  ${if infix then "infix " else ""}type Item = A
           |""".stripMargin
      val (classView, bodyView, typeStructureView) = decodeAllViews(source, traitName)
      val alias = typeStructureView.directTypeMembers
        .headOption
        .getOrElse(fail(s"missing normalized alias evidence for $traitName"))

      assertEquals(alias.name, "Item")
      assertEquals(alias.bodyIndex, 2)
      assertEquals(alias.kind, DirectTypeMemberKind.Alias)
      assertEquals(alias.typeParameters, Nil)
      assertEquals(alias.lowerBound, Bound.Absent)
      assertEquals(alias.upperBound, Bound.Absent)
      alias.aliasTarget match
        case Some(DirectTypeShape.EnclosingTypeParameter("A", _)) => ()
        case other => fail(s"unexpected alias target for $traitName: $other")
      assertEquals(alias.modifiers.unsupportedFlags, if infix then List("infix") else Nil)

      val decoded = InstanceSourceShapeDecoder.decode(
        classView,
        bodyView,
        Some(typeStructureView)
      )
      if infix then
        assertRejected(
          decoded,
          traitName,
          "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
        )
      else assert(decoded.isRight, clue(decoded))
  }

  test("rejects adjacent third-position type-member shapes through normalized evidence") {
    val rows = List(
      (
        "AbstractItem",
        "type Item",
        "inherited type member `Item` must be a concrete alias"
      ),
      (
        "NamedItem",
        "type Item = String",
        "inherited concrete type alias `Item` must target enclosing type parameter `A`"
      ),
      (
        "AppliedItem",
        "type Item = List[A]",
        "inherited concrete type alias `Item` must target enclosing type parameter `A`"
      ),
      (
        "QualifiedItem",
        "type Item = scala.Predef.String",
        "inherited concrete type alias `Item` must target enclosing type parameter `A`"
      ),
      (
        "PolymorphicItem",
        "type Item[B] = A",
        "inherited concrete type alias `Item` must not declare type parameters"
      ),
      (
        "PrivateItem",
        "private type Item = A",
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      ),
      (
        "ProtectedItem",
        "protected type Item = A",
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      ),
      (
        "FinalItem",
        "final type Item = A",
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      ),
      (
        "OverrideItem",
        "override type Item = A",
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      ),
      (
        "OpaqueItem",
        "opaque type Item = A",
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      )
    )

    rows.foreach: (traitName, aliasDeclaration, reason) =>
      val source =
        s"""trait $traitName[A]:
           |  def empty: A
           |  def combine(a: A, a1: A): A
           |  $aliasDeclaration
           |""".stripMargin
      assertRejected(decodeEither(source, traitName), traitName, reason)
  }

  test("rejects annotated and malformed normalized alias facts") {
    val source =
      """trait AnnotatedItem[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |""".stripMargin
    val (classView, bodyView, typeStructureView) = decodeAllViews(
      source,
      "AnnotatedItem"
    )
    val alias = typeStructureView.directTypeMembers.head

    val invalidAliases = List(
      (
        alias.copy(
          modifiers = alias.modifiers.copy(
            hasAnnotations = true,
            annotationCount = 1
          )
        ),
        "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
      ),
      (
        alias.copy(lowerBound = Bound.Present(alias.aliasTarget.get)),
        "inherited concrete type alias `Item` must not declare lower or upper bounds"
      ),
      (
        alias.copy(upperBound = Bound.Present(alias.aliasTarget.get)),
        "inherited concrete type alias `Item` must not declare lower or upper bounds"
      )
    )

    invalidAliases.foreach: (invalidAlias, reason) =>
      assertRejected(
        InstanceSourceShapeDecoder.decode(
          classView,
          bodyView,
          Some(typeStructureView.copy(directTypeMembers = List(invalidAlias)))
        ),
        "AnnotatedItem",
        reason
      )
  }

  test("rejects type-member evidence whose body index does not match the tail member") {
    val source =
      """trait MismatchedAliasIndex[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |""".stripMargin
    val (classView, bodyView, typeStructureView) = decodeAllViews(
      source,
      "MismatchedAliasIndex"
    )
    val alias = typeStructureView.directTypeMembers.head

    assertRejected(
      InstanceSourceShapeDecoder.decode(
        classView,
        bodyView,
        Some(
          typeStructureView.copy(
            directTypeMembers = List(alias.copy(bodyIndex = 3))
          )
        )
      ),
      "MismatchedAliasIndex",
      "direct body member at index 2 must provide normalized type-member evidence"
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
      "direct body member at index 2 must be a method; found val"
    ),
    (
      "third abstract method",
      """trait ThirdAbstract[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A
        |""".stripMargin,
      "ThirdAbstract",
      "inherited method `twice` must be concrete"
    ),
    (
      "valid inherited method followed by empty-clause method",
      """trait ValidThenEmptyClause[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |  def zero(): A = empty
        |""".stripMargin,
      "ValidThenEmptyClause",
      "inherited concrete method `zero` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "empty-clause method followed by valid inherited method",
      """trait EmptyClauseThenValid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zero(): A = empty
        |  def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "EmptyClauseThenValid",
      "inherited concrete method `zero` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "valid inherited method followed by concrete val",
      """trait ValidThenVal[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A): A = combine(a, a)
        |  val cached: A = empty
        |""".stripMargin,
      "ValidThenVal",
      "direct body member at index 3 must be a method; found val"
    ),
    (
      "polymorphic concrete method",
      """trait PolyConcrete[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice[B](a: A): A = combine(a, a)
        |""".stripMargin,
      "PolyConcrete",
      "inherited concrete method `twice` must not declare method type parameters"
    ),
    (
      "protected concrete method",
      """trait ProtectedConcrete[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  protected def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "ProtectedConcrete",
      "inherited concrete method `twice` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "private concrete method",
      """trait PrivateConcrete[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  private def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "PrivateConcrete",
      "inherited concrete method `twice` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated concrete method",
      """trait AnnotatedConcrete[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  @deprecated def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "AnnotatedConcrete",
      "inherited concrete method `twice` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "inline concrete method",
      """trait InlineConcrete[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  inline def twice(a: A): A = combine(a, a)
        |""".stripMargin,
      "InlineConcrete",
      "inherited concrete method `twice` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "empty concrete parameter clause",
      """trait ConcreteEmptyClause[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zero(): A = empty
        |""".stripMargin,
      "ConcreteEmptyClause",
      "inherited concrete method `zero` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "curried concrete parameters",
      """trait ConcreteCurried[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A)(a1: A): A = combine(a, a1)
        |""".stripMargin,
      "ConcreteCurried",
      "inherited concrete method `twice` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "wrong early concrete parameter type",
      """trait ConcreteEarlyParameter[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: Other, b: A, c: A, d: A, e: A): A = b
        |""".stripMargin,
      "ConcreteEarlyParameter",
      "inherited concrete method `fold5` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "wrong middle concrete parameter type",
      """trait ConcreteMiddleParameter[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: A, b: A, c: Other, d: A, e: A): A = a
        |""".stripMargin,
      "ConcreteMiddleParameter",
      "inherited concrete method `fold5` parameter `c` must use enclosing type parameter `A`"
    ),
    (
      "wrong final concrete parameter type",
      """trait ConcreteFinalParameter[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: A, b: A, c: A, d: A, e: Other): A = a
        |""".stripMargin,
      "ConcreteFinalParameter",
      "inherited concrete method `fold5` parameter `e` must use enclosing type parameter `A`"
    ),
    (
      "wrong concrete result type",
      """trait ConcreteResult[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(a: A, a1: A): Other = ???
        |""".stripMargin,
      "ConcreteResult",
      "inherited concrete method `twice` result type must use enclosing type parameter `A`"
    ),
    (
      "defaulted concrete parameter",
      """trait ConcreteDefault[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: A, b: A, c: A, d: A = empty, e: A): A = a
        |""".stripMargin,
      "ConcreteDefault",
      "inherited concrete method `fold5` parameter `d` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "contextual concrete clause",
      """trait ConcreteContextual[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def twice(using a: A, a1: A): A = combine(a, a1)
        |""".stripMargin,
      "ConcreteContextual",
      "inherited concrete method `twice` parameter clause must be ordinary and non-contextual"
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

  test("rejects modified second parameters on an inherited concrete binary method") {
    val (classView, bodyView) = decodeViews(
      """trait BinaryDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def combineAgain(left: A, right: A): A = combine(left, right)
        |""".stripMargin,
      "BinaryDerivedMonoid"
    )
    val inheritedMember = bodyView.members(2)
    val inheritedMethod =
      inheritedMember.method.getOrElse(fail("missing inherited method"))
    val clause = inheritedMethod.parameterClauses.head
    val second = clause.parameters(1)

    List(
      clause.copy(isImplicit = true),
      clause.copy(isGiven = true)
    ).foreach: malformedClause =>
      val malformedBody = bodyView.copy(
        members = bodyView.members.updated(
          2,
          inheritedMember.copy(
            method = Some(
              inheritedMethod.copy(parameterClauses = malformedClause :: Nil)
            )
          )
        )
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedBody),
        "BinaryDerivedMonoid",
        "inherited concrete method `combineAgain` parameter clause must be ordinary and non-contextual"
      )

    List(
      second.copy(hasDefault = true),
      second.copy(isContextual = true),
      second.copy(isImplicit = true),
      second.copy(isGiven = true),
      second.copy(isVal = true),
      second.copy(isVar = true)
    ).foreach: malformedParameter =>
      val malformedClause = clause.copy(
        parameters = clause.parameters.updated(1, malformedParameter)
      )
      val malformedBody = bodyView.copy(
        members = bodyView.members.updated(
          2,
          inheritedMember.copy(
            method = Some(
              inheritedMethod.copy(parameterClauses = malformedClause :: Nil)
            )
          )
        )
      )
      assertRejected(
        InstanceSourceShapeDecoder.decode(classView, malformedBody),
        "BinaryDerivedMonoid",
        "inherited concrete method `combineAgain` parameter `right` must be ordinary, non-defaulted, and unmodified"
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
    val (classView, bodyView, typeStructureView) = decodeAllViews(
      source,
      traitName
    )
    InstanceSourceShapeDecoder.decode(
      classView,
      bodyView,
      Some(typeStructureView)
    )

  private def decodeAllViews(
      source: String,
      traitName: String
  ): (
      ExpansionTargetView,
      ExpansionTargetBodyView,
      ExpansionTargetTypeStructureView
  ) =
    val unit = CompilationUnit(s"${traitName}InstanceDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = parsePrimary(unit, traitName)
    val classView = ExpansionTargetView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val bodyView = ExpansionTargetBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val typeStructureView = ExpansionTargetTypeStructureView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (classView, bodyView, typeStructureView)

  private def decodeViews(
      source: String,
      traitName: String
  ): (ExpansionTargetView, ExpansionTargetBodyView) =
    val unit = CompilationUnit(s"${traitName}InstanceDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = parsePrimary(unit, traitName)
    val classView = ExpansionTargetView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val bodyView = ExpansionTargetBodyView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (classView, bodyView)

  private def parsePrimary(
      unit: CompilationUnit,
      traitName: String
  )(using Context): TypeDef =
    new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
