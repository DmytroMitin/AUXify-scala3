package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import scala.meta.*

import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetTypeStructureView, ExpansionTargetView}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape

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

  test("admits every supported inherited method arity after the required binary method") {
    val decoded = decode(
      """trait RichMonoid[A]:
        |  def combine(a: A, b: A): A
        |  def identity: A = ???
        |  def twice(value: A): A = combine(value, value)
        |  def fold3(left: A, middle: A, right: A): A = combine(combine(left, middle), right)
        |""".stripMargin,
      "RichMonoid"
    )

    assertEquals(decoded.evidenceParameterName, "inst")
  }

  test("admits aliases and concrete methods in arbitrary source order") {
    val sources = List(
      "type Item = A",
      "def twice(value: A): A = value\n  type Item = A",
      "type Item = A\n  def twice(value: A): A = value",
      "type First = A\n  def twice(value: A): A = value\n  type Second = A\n  def fold3(left: A, middle: A, right: A): A = right\n  type Third = A"
    )

    sources.zipWithIndex.foreach: (tail, index) =>
      val traitName = s"InterleavedSyntax$index"
      val source =
        s"trait $traitName[A]:\n  def combine(a: A, b: A): A\n  $tail\n"
      assert(
        decodeEitherWithTypeStructure(source, traitName).isRight,
        clues(traitName, decodeEitherWithTypeStructure(source, traitName))
      )
  }

  test("freshens evidence across concrete term roles while aliases stay type-only") {
    val methodCollision = decodeEitherWithTypeStructure(
      """trait FreshSyntax[Element]:
        |  def merge(left: Element, right: Element): Element
        |  type Value = Element
        |  def inst(inst1: Element): Element = inst1
        |  type inst2 = Element
        |  def inst2(inst3: Element, end: Element): Element = end
        |""".stripMargin,
      "FreshSyntax"
    ).fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(methodCollision.evidenceParameterName, "inst4")

    val aliasOnly = decodeEitherWithTypeStructure(
      """trait AliasOnlySyntax[A]:
        |  def combine(left: A, right: A): A
        |  type inst = A
        |  type inst1 = A
        |""".stripMargin,
      "AliasOnlySyntax"
    ).fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aliasOnly.evidenceParameterName, "inst")
  }

  test("matches every inherited alias by its exact source body index") {
    val source =
      """trait IndexedSyntax[A]:
        |  def combine(a: A, b: A): A
        |  type First = A
        |  def twice(value: A): A = value
        |  type Second = A
        |  type Third = A
        |""".stripMargin
    val (classView, bodyView, typeStructureView) = decodeAllViews(source, "IndexedSyntax")
    assertEquals(typeStructureView.directTypeMembers.map(_.bodyIndex), List(1, 3, 4))

    List(1, 3, 4).foreach: index =>
      val aliases = typeStructureView.directTypeMembers
      val atIndex = aliases.find(_.bodyIndex == index).getOrElse(fail(s"missing alias at $index"))
      val missing = typeStructureView.copy(
        directTypeMembers = aliases.filterNot(_.bodyIndex == index)
      )
      assertRejected(
        SyntaxSourceShapeDecoder.decode(classView, bodyView, Some(missing)),
        "IndexedSyntax",
        s"direct body member at index $index must provide normalized type-member evidence"
      )

      val duplicate = typeStructureView.copy(directTypeMembers = aliases :+ atIndex)
      assertRejected(
        SyntaxSourceShapeDecoder.decode(classView, bodyView, Some(duplicate)),
        "IndexedSyntax",
        s"direct body member at index $index must provide exactly one normalized direct type member; found 2"
      )

      val wrongIndex = typeStructureView.copy(
        directTypeMembers = aliases.map(alias =>
          if alias.bodyIndex == index then alias.copy(bodyIndex = index + 10) else alias
        )
      )
      assertRejected(
        SyntaxSourceShapeDecoder.decode(classView, bodyView, Some(wrongIndex)),
        "IndexedSyntax",
        s"direct body member at index $index must provide normalized type-member evidence"
      )
  }

  test("validates the required binary method before the inherited tail") {
    assertRejected(
      decodeEitherWithTypeStructure(
        """trait RequiredFirst[A]:
          |  def combine(a: A): A
          |  val invalid: A
          |""".stripMargin,
        "RequiredFirst"
      ),
      "RequiredFirst",
      "direct method `combine` requires exactly two ordinary parameters; found 1"
    )
  }

  private val rejectedTailShapes = List(
    (
      "abstract method",
      "trait AbstractTail[A]:\n  def combine(a: A, b: A): A\n  def other(a: A): A\n",
      "AbstractTail",
      "inherited method `other` must be concrete"
    ),
    (
      "abstract type",
      "trait AbstractTypeTail[A]:\n  def combine(a: A, b: A): A\n  type Item\n",
      "AbstractTypeTail",
      "inherited type member `Item` must be a concrete alias"
    ),
    (
      "concrete val",
      "trait ValueTail[A]:\n  def combine(a: A, b: A): A\n  val other: A = ???\n",
      "ValueTail",
      "direct body member at index 1 must be a method; found val"
    ),
    (
      "concrete var",
      "trait VariableTail[A]:\n  def combine(a: A, b: A): A\n  var other: A = ???\n",
      "VariableTail",
      "direct body member at index 1 must be a method; found var"
    ),
    (
      "lazy val",
      "trait LazyValueTail[A]:\n  def combine(a: A, b: A): A\n  lazy val other: A = ???\n",
      "LazyValueTail",
      "direct body member at index 1 must be a method; found val"
    ),
    (
      "nested class",
      "trait NestedClassTail[A]:\n  def combine(a: A, b: A): A\n  class Other\n",
      "NestedClassTail",
      "direct body member at index 1 must be a method; found nested class"
    ),
    (
      "nested trait",
      "trait NestedTraitTail[A]:\n  def combine(a: A, b: A): A\n  trait Other\n",
      "NestedTraitTail",
      "direct body member at index 1 must be a method; found nested trait"
    ),
    (
      "nested object",
      "trait NestedObjectTail[A]:\n  def combine(a: A, b: A): A\n  object Other\n",
      "NestedObjectTail",
      "direct body member at index 1 must be a method; found nested object"
    ),
    (
      "wrong alias target",
      "trait WrongAliasTail[A]:\n  def combine(a: A, b: A): A\n  type Item = String\n",
      "WrongAliasTail",
      "inherited concrete type alias `Item` must target enclosing type parameter `A`"
    ),
    (
      "bounded alias",
      "trait BoundedAliasTail[A]:\n  def combine(a: A, b: A): A\n  type Item <: A\n",
      "BoundedAliasTail",
      "inherited type member `Item` must be a concrete alias"
    ),
    (
      "polymorphic alias",
      "trait PolymorphicAliasTail[A]:\n  def combine(a: A, b: A): A\n  type Item[B] = A\n",
      "PolymorphicAliasTail",
      "inherited concrete type alias `Item` must not declare type parameters"
    ),
    (
      "private alias",
      "trait PrivateAliasTail[A]:\n  def combine(a: A, b: A): A\n  private type Item = A\n",
      "PrivateAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated alias",
      "trait AnnotatedAliasTail[A]:\n  def combine(a: A, b: A): A\n  @deprecated type Item = A\n",
      "AnnotatedAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "empty method clause",
      "trait EmptyClauseTail[A]:\n  def combine(a: A, b: A): A\n  def other(): A = ???\n",
      "EmptyClauseTail",
      "inherited concrete method `other` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "curried method",
      "trait CurriedTail[A]:\n  def combine(a: A, b: A): A\n  def other(a: A)(b: A): A = a\n",
      "CurriedTail",
      "inherited concrete method `other` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "contextual method",
      "trait ContextualTail[A]:\n  def combine(a: A, b: A): A\n  def other(using a: A): A = a\n",
      "ContextualTail",
      "inherited concrete method `other` parameter clause must be ordinary and non-contextual"
    ),
    (
      "defaulted method",
      "trait DefaultedTail[A]:\n  def combine(a: A, b: A): A\n  def other(a: A = ???): A = a\n",
      "DefaultedTail",
      "inherited concrete method `other` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "polymorphic method",
      "trait PolymorphicTail[A]:\n  def combine(a: A, b: A): A\n  def other[B](a: A): A = a\n",
      "PolymorphicTail",
      "inherited concrete method `other` must not declare method type parameters"
    ),
    (
      "modifier-bearing method",
      "trait InfixTail[A]:\n  def combine(a: A, b: A): A\n  infix def other(a: A): A = a\n",
      "InfixTail",
      "inherited concrete method `other` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "wrong method parameter",
      "trait WrongTailParameter[A]:\n  def combine(a: A, b: A): A\n  def other(a: String): A = ???\n",
      "WrongTailParameter",
      "inherited concrete method `other` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "wrong method result",
      "trait WrongTailResult[A]:\n  def combine(a: A, b: A): A\n  def other(a: A): String = a.toString\n",
      "WrongTailResult",
      "inherited concrete method `other` result type must use enclosing type parameter `A`"
    ),
    (
      "late invalid member",
      "trait LateInvalidTail[A]:\n  def combine(a: A, b: A): A\n  type First = A\n  def twice(a: A): A = a\n  type Second = A\n  val invalid: A = ???\n",
      "LateInvalidTail",
      "direct body member at index 4 must be a method; found val"
    )
  )

  rejectedTailShapes.foreach: (label, source, traitName, reason) =>
    test(s"rejects inherited tail $label") {
      assertRejected(decodeEitherWithTypeStructure(source, traitName), traitName, reason)
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
      "object syntax { extension [Collision1](left: Collision1) def merge(right: Collision1)(using inst: Collision[Collision1]): Collision1 = inst.merge(left, right) }"
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
      "direct body member at index 1 must be a method; found val"
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
    assert(diagnostic.pos.span.exists, clues(diagnostic))

  private def decode(
      source: String,
      traitName: String
  ): SyntaxSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(source: String, traitName: String) =
    val (classView, bodyView) = decodeViews(source, traitName)
    SyntaxSourceShapeDecoder.decode(classView, bodyView)

  private def decodeEitherWithTypeStructure(source: String, traitName: String) =
    val (classView, bodyView, typeStructureView) = decodeAllViews(source, traitName)
    SyntaxSourceShapeDecoder.decode(classView, bodyView, Some(typeStructureView))

  private def decodeAllViews(
      source: String,
      traitName: String
  ): (
      ExpansionTargetView,
      ExpansionTargetBodyView,
      ExpansionTargetTypeStructureView
  ) =
    val unit = CompilationUnit(s"${traitName}SyntaxDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val classView = ExpansionTargetView.decode(primary).fold(d => fail(d.message), identity)
    val bodyView = ExpansionTargetBodyView.decode(primary).fold(d => fail(d.message), identity)
    val typeStructureView = ExpansionTargetTypeStructureView
      .decode(primary)
      .fold(d => fail(d.message), identity)
    (classView, bodyView, typeStructureView)
  private def decodeViews(
      source: String,
      traitName: String
  ): (ExpansionTargetView, ExpansionTargetBodyView) =
    val unit = CompilationUnit(s"${traitName}SyntaxDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
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
