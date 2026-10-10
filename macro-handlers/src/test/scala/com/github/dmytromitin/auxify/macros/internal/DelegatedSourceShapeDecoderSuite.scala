package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionTargetBodyView,
  ExpansionTargetTypeStructureView,
  ExpansionTargetView
}
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

  test("decodes a unary primary followed by one inherited concrete method") {
    assertEquals(
      decode(
        """trait RichShow[A]:
          |  def show(a: A): String
          |  def duplicate(a: A): A = a
          |""".stripMargin,
        "RichShow"
      ),
      DelegatedSourceShapeDecoder.SourceShape(
        traitName = "RichShow",
        typeParameterName = "A",
        methodName = "show",
        variant = DelegatedSourceShapeDecoder.Variant.Unary(
          parameterName = "a",
          resultTypeName = "String"
        ),
        occupiedTermNames = Set("duplicate", "a")
      )
    )
  }

  test("admits both primary families with every supported inherited method arity") {
    val rows = List(
      (
        "RichUnary",
        """trait RichUnary[A]:
          |  def show(a: A): String
          |  def identity: A = ???
          |  def duplicate(a: A): A = a
          |  def pick(a: A, b: A): A = a
          |""".stripMargin,
        Set("identity", "duplicate", "a", "pick", "b")
      ),
      (
        "RichParameterless",
        """trait RichParameterless[A]:
          |  def empty: A
          |  def identity: A = ???
          |  def duplicate(a: A): A = a
          |  def pick(a: A, b: A): A = a
          |""".stripMargin,
        Set("identity", "duplicate", "a", "pick", "b")
      )
    )

    rows.foreach: (traitName, source, occupied) =>
      assertEquals(
        decodeEitherWithTypeStructure(source, traitName)
          .fold(diagnostic => fail(diagnostic.message), identity)
          .occupiedTermNames,
        occupied
      )
  }

  test("admits aliases and methods in every source order for both primary families") {
    val primaryRows = List(
      ("Unary", "def show(a: A): String"),
      ("Parameterless", "def empty: A")
    )
    val tailRows = List(
      ("AliasOnly", "type Item = A"),
      ("MethodThenAlias", "def twice(a: A): A = a\n  type Item = A"),
      ("AliasThenMethod", "type Item = A\n  def twice(a: A): A = a"),
      (
        "Interleaved",
        "type First = A\n  def twice(a: A): A = a\n  type Second = A\n  def pick(a: A, b: A): A = b\n  type Third = A"
      )
    )

    for
      (primaryLabel, primary) <- primaryRows
      (tailLabel, tail) <- tailRows
    do
      val traitName = s"${primaryLabel}${tailLabel}"
      val source = s"trait $traitName[A]:\n  $primary\n  $tail\n"
      assert(
        decodeEitherWithTypeStructure(source, traitName).isRight,
        clues(traitName, decodeEitherWithTypeStructure(source, traitName))
      )
  }

  test("freshens across inherited method roles while aliases stay outside the term namespace") {
    val decoded = decodeEitherWithTypeStructure(
      """trait FreshDelegated[Element]:
        |  def render(value: Element): Text
        |  type inst = Element
        |  def inst(inst1: Element): Element = inst1
        |  type Value = Element
        |  def inst2(inst3: Element, end: Element): Element = end
        |""".stripMargin,
      "FreshDelegated"
    ).fold(diagnostic => fail(diagnostic.message), identity)

    assertEquals(decoded.occupiedTermNames, Set("inst", "inst1", "inst2", "inst3", "end"))
  }

  test("matches every inherited alias by its exact source body index") {
    val source =
      """trait IndexedDelegated[A]:
        |  def show(a: A): String
        |  type First = A
        |  def twice(a: A): A = a
        |  type Second = A
        |  type Third = A
        |""".stripMargin
    val (classView, bodyView, typeStructureView) = decodeAllViews(source, "IndexedDelegated")
    assertEquals(typeStructureView.directTypeMembers.map(_.bodyIndex), List(1, 3, 4))

    List(1, 3, 4).foreach: index =>
      val aliases = typeStructureView.directTypeMembers
      val atIndex = aliases.find(_.bodyIndex == index).getOrElse(fail(s"missing alias at $index"))
      val missing = typeStructureView.copy(
        directTypeMembers = aliases.filterNot(_.bodyIndex == index)
      )
      assertRejected(
        DelegatedSourceShapeDecoder.decode("IndexedDelegated", classView, bodyView, Some(missing)),
        "IndexedDelegated",
        s"direct body member at index $index must provide normalized type-member evidence"
      )

      val duplicate = typeStructureView.copy(directTypeMembers = aliases :+ atIndex)
      assertRejected(
        DelegatedSourceShapeDecoder.decode("IndexedDelegated", classView, bodyView, Some(duplicate)),
        "IndexedDelegated",
        s"direct body member at index $index must provide exactly one normalized direct type member; found 2"
      )

      val mismatched = typeStructureView.copy(
        directTypeMembers = aliases.map(alias =>
          if alias.bodyIndex == index then alias.copy(bodyIndex = index + 10) else alias
        )
      )
      assertRejected(
        DelegatedSourceShapeDecoder.decode("IndexedDelegated", classView, bodyView, Some(mismatched)),
        "IndexedDelegated",
        s"direct body member at index $index must provide normalized type-member evidence"
      )
  }

  test("validates the required primary method before any inherited tail member") {
    assertRejected(
      decodeEitherWithTypeStructure(
        """trait RequiredFirst[A]:
          |  def show(a: A): List[String]
          |  val invalid: A
          |""".stripMargin,
        "RequiredFirst"
      ),
      "RequiredFirst",
      "direct method `show` result type must be one unqualified named type"
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

  private val rejectedTailShapes = List(
    (
      "abstract inherited method",
      """trait AbstractTail[A]:
        |  def show(a: A): String
        |  def other(a: A): A
        |""".stripMargin,
      "AbstractTail",
      "inherited method `other` must be concrete"
    ),
    (
      "explicit empty clause",
      """trait EmptyClauseTail[A]:
        |  def show(a: A): String
        |  def other(): A = ???
        |""".stripMargin,
      "EmptyClauseTail",
      "inherited concrete method `other` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "curried inherited method",
      """trait CurriedTail[A]:
        |  def show(a: A): String
        |  def other(a: A)(b: A): A = a
        |""".stripMargin,
      "CurriedTail",
      "inherited concrete method `other` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "contextual inherited method",
      """trait ContextualTail[A]:
        |  def show(a: A): String
        |  def other(using a: A): A = a
        |""".stripMargin,
      "ContextualTail",
      "inherited concrete method `other` parameter clause must be ordinary and non-contextual"
    ),
    (
      "defaulted inherited parameter",
      """trait DefaultedTail[A]:
        |  def show(a: A): String
        |  def other(a: A = ???): A = a
        |""".stripMargin,
      "DefaultedTail",
      "inherited concrete method `other` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "polymorphic inherited method",
      """trait PolymorphicTail[A]:
        |  def show(a: A): String
        |  def other[B](a: A): A = a
        |""".stripMargin,
      "PolymorphicTail",
      "inherited concrete method `other` must not declare method type parameters"
    ),
    (
      "modifier-bearing inherited method",
      """trait InfixTail[A]:
        |  def show(a: A): String
        |  infix def other(a: A): A = a
        |""".stripMargin,
      "InfixTail",
      "inherited concrete method `other` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "private inherited method",
      """trait PrivateMethodTail[A]:
        |  def show(a: A): String
        |  private def other(a: A): A = a
        |""".stripMargin,
      "PrivateMethodTail",
      "inherited concrete method `other` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated inherited method",
      """trait AnnotatedMethodTail[A]:
        |  def show(a: A): String
        |  @deprecated def other(a: A): A = a
        |""".stripMargin,
      "AnnotatedMethodTail",
      "inherited concrete method `other` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "wrong inherited parameter type",
      """trait WrongParameterTail[A]:
        |  def show(a: A): String
        |  def other(a: String): A = ???
        |""".stripMargin,
      "WrongParameterTail",
      "inherited concrete method `other` parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "wrong inherited result type",
      """trait WrongResultTail[A]:
        |  def show(a: A): String
        |  def other(a: A): String = a.toString
        |""".stripMargin,
      "WrongResultTail",
      "inherited concrete method `other` result type must use enclosing type parameter `A`"
    ),
    (
      "abstract inherited type",
      """trait AbstractTypeTail[A]:
        |  def show(a: A): String
        |  type Item
        |""".stripMargin,
      "AbstractTypeTail",
      "inherited type member `Item` must be a concrete alias"
    ),
    (
      "bounded inherited type",
      """trait BoundedTypeTail[A]:
        |  def show(a: A): String
        |  type Item <: A
        |""".stripMargin,
      "BoundedTypeTail",
      "inherited type member `Item` must be a concrete alias"
    ),
    (
      "polymorphic inherited alias",
      """trait PolymorphicAliasTail[A]:
        |  def show(a: A): String
        |  type Item[B] = A
        |""".stripMargin,
      "PolymorphicAliasTail",
      "inherited concrete type alias `Item` must not declare type parameters"
    ),
    (
      "private inherited alias",
      """trait PrivateAliasTail[A]:
        |  def show(a: A): String
        |  private type Item = A
        |""".stripMargin,
      "PrivateAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated inherited alias",
      """trait AnnotatedAliasTail[A]:
        |  def show(a: A): String
        |  @deprecated type Item = A
        |""".stripMargin,
      "AnnotatedAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "wrong inherited alias target",
      """trait WrongAliasTail[A]:
        |  def show(a: A): String
        |  type Item = String
        |""".stripMargin,
      "WrongAliasTail",
      "inherited concrete type alias `Item` must target enclosing type parameter `A`"
    ),
    (
      "late invalid member",
      """trait LateInvalidTail[A]:
        |  def empty: A
        |  type First = A
        |  def twice(a: A): A = a
        |  type Second = A
        |  val invalid: A
        |""".stripMargin,
      "LateInvalidTail",
      "direct body member at index 4 must be a method; found val"
    )
  )

  rejectedTailShapes.foreach: (label, source, traitName, reason) =>
    test(s"rejects tail $label") {
      assertRejected(decodeEitherWithTypeStructure(source, traitName), traitName, reason)
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
      "direct body member at index 1 must be a method; found val"
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

  private def decodeEitherWithTypeStructure(source: String, traitName: String) =
    val (classView, bodyView, typeStructureView) = decodeAllViews(source, traitName)
    DelegatedSourceShapeDecoder.decode(
      traitName,
      classView,
      bodyView,
      Some(typeStructureView)
    )

  private def assertRejected(
      decoded: Either[ExpansionDiagnostic, DelegatedSourceShapeDecoder.SourceShape],
      traitName: String,
      reason: String
  ): Unit =
    val diagnostic = decoded.left.toOption.getOrElse(fail(s"$traitName unexpectedly decoded"))
    assertEquals(
      diagnostic.message,
      s"unsupported @delegated source shape for `$traitName`: $reason"
    )
    assert(diagnostic.pos.span.exists, clues(diagnostic))

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

  private def decodeAllViews(
      source: String,
      traitName: String
  ): (
      ExpansionTargetView,
      ExpansionTargetBodyView,
      ExpansionTargetTypeStructureView
  ) =
    val unit = CompilationUnit(s"${traitName}DelegatedDecoderFixture.scala", source)
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
