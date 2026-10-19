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

  test("admits a heterogeneous inherited tail and freshens past tail aliases") {
    assertEquals(
      decode(
        """trait RichHasOut[A]:
          |  type Out
          |  type Out0 = A
          |  def identity(a: A): A = a
          |  type Out1 = Out0
          |  type Copy = Out1
          |  def pick(a: A, b: A): A = a
          |""".stripMargin,
        "RichHasOut"
      ),
      InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape(
        traitName = "RichHasOut",
        enclosingTypeParameterName = "A",
        memberName = "Out",
        generatedTypeParameterName = "Out2"
      )
    )
  }

  test("admits alias chains rooted in the enclosing parameter but not the abstract result") {
    val decoded = decode(
      """trait AbstractTypeAliasChain[Element]:
        |  type Result
        |  type Item = Element
        |  type Copy = Item
        |  def retain(value: Element): Element = value
        |  type More = Copy
        |  type Branch = Item
        |""".stripMargin,
      "AbstractTypeAliasChain"
    )

    assertEquals(decoded.memberName, "Result")
    assertEquals(decoded.generatedTypeParameterName, "Result0")
  }

  test("rejects the leading abstract result as an inherited method type root") {
    assertRejected(
      decodeEither(
        """trait AbstractResultMethod[A]:
          |  type Out
          |  def identity(value: Out): Out = value
          |""".stripMargin,
        "AbstractResultMethod"
      ),
      "AbstractResultMethod",
      "inherited concrete method `identity` parameter `value` must use enclosing type parameter `A` or an earlier validated concrete alias"
    )
  }

  test("rejects a malformed tail alias that duplicates the leading abstract result name") {
    assertRejected(
      decodeEither(
        """trait AbstractResultAliasCollision[A]:
          |  type Out
          |  type Out = A
          |""".stripMargin,
        "AbstractResultAliasCollision"
      ),
      "AbstractResultAliasCollision",
      "inherited concrete type alias `Out` must not duplicate the enclosing type parameter or an earlier direct type member"
    )
  }

  test("admits alias-typed inherited methods without admitting the abstract result root") {
    val decoded = decode(
      """trait AbstractAliasMethods[Element]:
        |  type Result
        |  type Result0 = Element
        |  type Result1 = Result0
        |  def identity(value: Result0): Result1 = value
        |  def mixed(left: Element, right: Result1): Result0 = left
        |  def zeroAlias: Result1 = throw new NoSuchElementException
        |""".stripMargin,
      "AbstractAliasMethods"
    )

    assertEquals(decoded.memberName, "Result")
    assertEquals(decoded.generatedTypeParameterName, "Result2")
  }

  test("admits every supported inherited tail topology") {
    val rows = List(
      "AliasOnly" ->
        """trait AliasOnly[A]:
          |  type Out
          |  type Item = A
          |""".stripMargin,
      "ZeroMethod" ->
        """trait ZeroMethod[A]:
          |  type Out
          |  def zero: A = ???
          |""".stripMargin,
      "UnaryMethod" ->
        """trait UnaryMethod[A]:
          |  type Out
          |  def identity(value: A): A = value
          |""".stripMargin,
      "LargerMethod" ->
        """trait LargerMethod[A]:
          |  type Out
          |  def choose(a: A, b: A, c: A): A = a
          |""".stripMargin,
      "AliasThenMethod" ->
        """trait AliasThenMethod[A]:
          |  type Out
          |  type Item = A
          |  def identity(value: A): A = value
          |""".stripMargin,
      "MethodThenAlias" ->
        """trait MethodThenAlias[A]:
          |  type Out
          |  def identity(value: A): A = value
          |  type Item = A
          |""".stripMargin,
      "Interleaved" ->
        """trait Interleaved[A]:
          |  type Out
          |  type Item = A
          |  def zero: A = ???
          |  type Value = A
          |  def choose(a: A, b: A, c: A): A = a
          |""".stripMargin
    )

    rows.foreach: (traitName, source) =>
      assert(
        decodeEither(source, traitName).isRight,
        clues(traitName, decodeEither(source, traitName))
      )
  }

  test("keeps term-only Out0 spellings out of the generated type namespace") {
    assertEquals(
      decode(
        """trait TermOnlyCollision[A]:
          |  type Out
          |  def Out0(Out1: A): A = Out1
          |""".stripMargin,
        "TermOnlyCollision"
      ).generatedTypeParameterName,
      "Out0"
    )
  }

  test("matches every inherited alias by its exact source body index") {
    val source =
      """trait IndexedAliases[A]:
        |  type Out
        |  type First = A
        |  def identity(value: A): A = value
        |  type Second = A
        |  type Third = A
        |""".stripMargin
    val (classView, bodyView, typeStructureView) =
      decodeAllViews(source, "IndexedAliases")
    assertEquals(
      typeStructureView.directTypeMembers.map(_.bodyIndex),
      List(0, 1, 3, 4)
    )

    List(1, 3, 4).foreach: index =>
      val records = typeStructureView.directTypeMembers
      val atIndex = records.find(_.bodyIndex == index).getOrElse(
        fail(s"missing fixture alias at $index")
      )
      val missing = typeStructureView.copy(
        directTypeMembers = records.filterNot(_.bodyIndex == index)
      )
      assertRejected(
        InstanceAbstractTypeMemberSourceShapeDecoder.decode(
          classView,
          bodyView,
          missing
        ),
        "IndexedAliases",
        s"direct body member at index $index must provide normalized type-member evidence"
      )

      val duplicate = typeStructureView.copy(
        directTypeMembers = records :+ atIndex
      )
      assertRejected(
        InstanceAbstractTypeMemberSourceShapeDecoder.decode(
          classView,
          bodyView,
          duplicate
        ),
        "IndexedAliases",
        s"direct body member at index $index must provide exactly one normalized direct type member; found 2"
      )

      val mismatched = typeStructureView.copy(
        directTypeMembers = records.map(record =>
          if record.bodyIndex == index then record.copy(bodyIndex = index + 10)
          else record
        )
      )
      assertRejected(
        InstanceAbstractTypeMemberSourceShapeDecoder.decode(
          classView,
          bodyView,
          mismatched
        ),
        "IndexedAliases",
        s"direct body member at index $index must provide normalized type-member evidence"
      )
  }

  private val rejectedSources = List(
    (
      "zero direct body members",
      """trait Empty[A]
        |""".stripMargin,
      "Empty",
      "abstract-type-member family requires at least one direct body member; found 0"
    ),
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
      "inherited type member `Other` must be a concrete alias"
    ),
    (
      "abstract type member plus method",
      """trait TypeThenMethod[A]:
        |  type Out
        |  def value: A
        |""".stripMargin,
      "TypeThenMethod",
      "inherited method `value` must be concrete"
    ),
    (
      "method plus abstract type member",
      """trait MethodThenType[A]:
        |  def value: A
        |  type Out
        |""".stripMargin,
      "MethodThenType",
      "direct body member at index 0 must be an abstract type member; found method"
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

  private val rejectedTailSources = List(
    (
      "abstract result as alias root",
      "trait AbstractResultRoot[A]:\n  type Out\n  type Item = Out\n",
      "AbstractResultRoot",
      "inherited concrete type alias `Item` must target enclosing type parameter `A` or an earlier validated concrete alias"
    ),
    (
      "wrong alias target",
      "trait WrongAliasTail[A]:\n  type Out\n  type Item = String\n",
      "WrongAliasTail",
      "inherited concrete type alias `Item` must target enclosing type parameter `A` or an earlier validated concrete alias"
    ),
    (
      "bounded alias",
      "trait BoundedAliasTail[A]:\n  type Out\n  type Item <: A\n",
      "BoundedAliasTail",
      "inherited type member `Item` must be a concrete alias"
    ),
    (
      "polymorphic alias",
      "trait PolymorphicAliasTail[A]:\n  type Out\n  type Item[B] = A\n",
      "PolymorphicAliasTail",
      "inherited concrete type alias `Item` must not declare type parameters"
    ),
    (
      "private alias",
      "trait PrivateAliasTail[A]:\n  type Out\n  private type Item = A\n",
      "PrivateAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated alias",
      "trait AnnotatedAliasTail[A]:\n  type Out\n  @deprecated type Item = A\n",
      "AnnotatedAliasTail",
      "inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "abstract method",
      "trait AbstractMethodTail[A]:\n  type Out\n  def other(a: A): A\n",
      "AbstractMethodTail",
      "inherited method `other` must be concrete"
    ),
    (
      "concrete val",
      "trait ValueTail[A]:\n  type Out\n  val other: A = ???\n",
      "ValueTail",
      "direct body member at index 1 must be a method; found val"
    ),
    (
      "concrete var",
      "trait VariableTail[A]:\n  type Out\n  var other: A = ???\n",
      "VariableTail",
      "direct body member at index 1 must be a method; found var"
    ),
    (
      "lazy val",
      "trait LazyValueTail[A]:\n  type Out\n  lazy val other: A = ???\n",
      "LazyValueTail",
      "direct body member at index 1 must be a method; found val"
    ),
    (
      "nested class",
      "trait NestedClassTail[A]:\n  type Out\n  class Other\n",
      "NestedClassTail",
      "direct body member at index 1 must be a method; found nested class"
    ),
    (
      "nested trait",
      "trait NestedTraitTail[A]:\n  type Out\n  trait Other\n",
      "NestedTraitTail",
      "direct body member at index 1 must be a method; found nested trait"
    ),
    (
      "nested object",
      "trait NestedObjectTail[A]:\n  type Out\n  object Other\n",
      "NestedObjectTail",
      "direct body member at index 1 must be a method; found nested object"
    ),
    (
      "empty method clause",
      "trait EmptyClauseTail[A]:\n  type Out\n  def other(): A = ???\n",
      "EmptyClauseTail",
      "inherited concrete method `other` requires one or more ordinary parameters in its single clause; found 0"
    ),
    (
      "curried method",
      "trait CurriedTail[A]:\n  type Out\n  def other(a: A)(b: A): A = a\n",
      "CurriedTail",
      "inherited concrete method `other` requires exactly one ordinary parameter clause; found 2"
    ),
    (
      "contextual method",
      "trait ContextualTail[A]:\n  type Out\n  def other(using a: A): A = a\n",
      "ContextualTail",
      "inherited concrete method `other` parameter clause must be ordinary and non-contextual"
    ),
    (
      "defaulted method",
      "trait DefaultedTail[A]:\n  type Out\n  def other(a: A = ???): A = a\n",
      "DefaultedTail",
      "inherited concrete method `other` parameter `a` must be ordinary, non-defaulted, and unmodified"
    ),
    (
      "polymorphic method",
      "trait PolymorphicTail[A]:\n  type Out\n  def other[B](a: A): A = a\n",
      "PolymorphicTail",
      "inherited concrete method `other` must not declare method type parameters"
    ),
    (
      "wrong method parameter",
      "trait WrongParameterTail[A]:\n  type Out\n  def other(a: String): A = ???\n",
      "WrongParameterTail",
      "inherited concrete method `other` parameter `a` must use enclosing type parameter `A` or an earlier validated concrete alias"
    ),
    (
      "wrong method result",
      "trait WrongResultTail[A]:\n  type Out\n  def other(a: A): String = ???\n",
      "WrongResultTail",
      "inherited concrete method `other` result type must use enclosing type parameter `A` or an earlier validated concrete alias"
    ),
    (
      "late invalid member",
      "trait LateInvalidTail[A]:\n  type Out\n  type Item = A\n  def identity(a: A): A = a\n  type Value = A\n  val invalid: A = ???\n",
      "LateInvalidTail",
      "direct body member at index 4 must be a method; found val"
    )
  )

  rejectedTailSources.foreach: (label, source, traitName, reason) =>
    test(s"rejects inherited tail $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }

  rejectedSources.foreach: (label, source, traitName, reason) =>
    test(s"rejects $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }

  test("rejects missing duplicate and wrong-index normalized member records") {
    val (classView, bodyView, typeView) = decodeAllViews(CanonicalSource, "HasOut")
    val member = typeView.directTypeMembers.head
    List(
      typeView.copy(directTypeMembers = Nil) -> 0,
      typeView.copy(directTypeMembers = List(member, member)) -> 2,
      typeView.copy(directTypeMembers = List(member.copy(bodyIndex = 1))) -> 0
    ).foreach: (malformed, countAtIndexZero) =>
      assertRejected(
        InstanceAbstractTypeMemberSourceShapeDecoder.decode(
          classView,
          bodyView,
          malformed
        ),
        "HasOut",
        s"direct body member at index 0 must provide exactly one normalized type-member record at body index 0; found $countAtIndexZero"
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
