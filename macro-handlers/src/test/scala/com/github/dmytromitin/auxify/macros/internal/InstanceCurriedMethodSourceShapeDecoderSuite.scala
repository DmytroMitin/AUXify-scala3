package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetView}

class InstanceCurriedMethodSourceShapeDecoderSuite extends munit.FunSuite:
  test("decodes the canonical curried-method instance family") {
    assertEquals(
      decode(
        """trait Curried[A]:
          |  def combine(a: A)(b: A): A
          |""".stripMargin,
        "Curried"
      ),
      InstanceCurriedMethodSourceShapeDecoder.SourceShape(
        traitName = "Curried",
        enclosingTypeParameterName = "A",
        methodName = "combine",
        firstParameterName = "a",
        secondParameterName = "b",
        carrierName = "combineFunction"
      )
    )
  }

  test("derives renamed roles and freshens the carrier across generated-method term roles") {
    assertEquals(
      decode(
        """trait Chain[Element]:
          |  def combineFunction(left: Element)(right: Element): Element
          |""".stripMargin,
        "Chain"
      ),
      InstanceCurriedMethodSourceShapeDecoder.SourceShape(
        "Chain",
        "Element",
        "combineFunction",
        "left",
        "right",
        "combineFunction1"
      )
    )
    assertEquals(
      decode(
        """trait ParameterCollision[Element]:
          |  def append(combineFunction: Element)(right: Element): Element
          |""".stripMargin,
        "ParameterCollision"
      ).carrierName,
      "combineFunction1"
    )
  }

  private val rejectedSources = List(
    (
      "flattened binary clause",
      """trait Flattened[A]:
        |  def combine(a: A, b: A): A
        |""".stripMargin,
      "Flattened",
      "curried method `combine` requires exactly two ordinary parameter clauses; found 1"
    ),
    (
      "only one unary clause",
      """trait Unary[A]:
        |  def combine(a: A): A
        |""".stripMargin,
      "Unary",
      "curried method `combine` requires exactly two ordinary parameter clauses; found 1"
    ),
    (
      "three clauses",
      """trait Three[A]:
        |  def combine(a: A)(b: A)(c: A): A
        |""".stripMargin,
      "Three",
      "curried method `combine` requires exactly two ordinary parameter clauses; found 3"
    ),
    (
      "empty first clause",
      """trait EmptyFirst[A]:
        |  def combine()(b: A): A
        |""".stripMargin,
      "EmptyFirst",
      "curried method `combine` first parameter clause requires exactly one ordinary parameter; found 0"
    ),
    (
      "empty second clause",
      """trait EmptySecond[A]:
        |  def combine(a: A)(): A
        |""".stripMargin,
      "EmptySecond",
      "curried method `combine` second parameter clause requires exactly one ordinary parameter; found 0"
    ),
    (
      "contextual first clause",
      """trait ContextualFirst[A]:
        |  def combine(using a: A)(b: A): A
        |""".stripMargin,
      "ContextualFirst",
      "curried method `combine` first parameter clause must be ordinary and non-contextual"
    ),
    (
      "contextual second clause",
      """trait ContextualSecond[A]:
        |  def combine(a: A)(using b: A): A
        |""".stripMargin,
      "ContextualSecond",
      "curried method `combine` second parameter clause must be ordinary and non-contextual"
    ),
    (
      "two parameters in first clause",
      """trait TwoFirst[A]:
        |  def combine(a: A, x: A)(b: A): A
        |""".stripMargin,
      "TwoFirst",
      "curried method `combine` first parameter clause requires exactly one ordinary parameter; found 2"
    ),
    (
      "two parameters in second clause",
      """trait TwoSecond[A]:
        |  def combine(a: A)(b: A, x: A): A
        |""".stripMargin,
      "TwoSecond",
      "curried method `combine` second parameter clause requires exactly one ordinary parameter; found 2"
    ),
    (
      "defaulted first parameter",
      """trait DefaultFirst[A]:
        |  def combine(a: A = ???)(b: A): A
        |""".stripMargin,
      "DefaultFirst",
      "curried method `combine` first parameter `a` must be named, ordinary, non-defaulted, and unmodified"
    ),
    (
      "defaulted second parameter",
      """trait DefaultSecond[A]:
        |  def combine(a: A)(b: A = a): A
        |""".stripMargin,
      "DefaultSecond",
      "curried method `combine` second parameter `b` must be named, ordinary, non-defaulted, and unmodified"
    ),
    (
      "wrong first parameter type",
      """trait WrongFirst[A]:
        |  def combine(a: String)(b: A): A
        |""".stripMargin,
      "WrongFirst",
      "curried method `combine` first parameter `a` must use enclosing type parameter `A`"
    ),
    (
      "wrong second parameter type",
      """trait WrongSecond[A]:
        |  def combine(a: A)(b: String): A
        |""".stripMargin,
      "WrongSecond",
      "curried method `combine` second parameter `b` must use enclosing type parameter `A`"
    ),
    (
      "wrong result type",
      """trait WrongResult[A]:
        |  def combine(a: A)(b: A): String
        |""".stripMargin,
      "WrongResult",
      "curried method `combine` result type must use enclosing type parameter `A`"
    ),
    (
      "method type parameter",
      """trait Polymorphic[A]:
        |  def combine[B](a: A)(b: A): A
        |""".stripMargin,
      "Polymorphic",
      "curried method `combine` must not declare method type parameters"
    ),
    (
      "concrete method",
      """trait Concrete[A]:
        |  def combine(a: A)(b: A): A = a
        |""".stripMargin,
      "Concrete",
      "curried method `combine` must be abstract"
    ),
    (
      "private method",
      """trait PrivateMethod[A]:
        |  private def combine(a: A)(b: A): A
        |""".stripMargin,
      "PrivateMethod",
      "curried method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "protected method",
      """trait ProtectedMethod[A]:
        |  protected def combine(a: A)(b: A): A
        |""".stripMargin,
      "ProtectedMethod",
      "curried method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "annotated method",
      """trait AnnotatedMethod[A]:
        |  @deprecated def combine(a: A)(b: A): A
        |""".stripMargin,
      "AnnotatedMethod",
      "curried method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "modifier-bearing method",
      """trait InfixMethod[A]:
        |  infix def combine(a: A)(b: A): A
        |""".stripMargin,
      "InfixMethod",
      "curried method `combine` must be public, unannotated, and free of unsupported modifiers"
    ),
    (
      "zero owner parameters",
      """trait ZeroOwner:
        |  def combine(a: Int)(b: Int): Int
        |""".stripMargin,
      "ZeroOwner",
      "curried-method family requires exactly one enclosing type parameter; found 0"
    ),
    (
      "two owner parameters",
      """trait TwoOwner[A, B]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "TwoOwner",
      "curried-method family requires exactly one enclosing type parameter; found 2"
    ),
    (
      "variant owner",
      """trait Variant[+A]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "Variant",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "bounded owner",
      """trait Bounded[A <: Matchable]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "Bounded",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "higher-kinded owner",
      """trait Higher[F[_]]:
        |  def combine(a: F)(b: F): F
        |""".stripMargin,
      "Higher",
      "enclosing type parameter `F` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "context-bounded owner",
      """trait ContextBound[A: Ordering]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "ContextBound",
      "enclosing type parameter `A` must be invariant, ordinary, unbounded, non-higher-kinded, and free of context bounds"
    ),
    (
      "extra direct member",
      """trait Extra[A]:
        |  def combine(a: A)(b: A): A
        |  def other: A
        |""".stripMargin,
      "Extra",
      "curried-method family requires exactly one direct body member; found 2"
    )
  )

  rejectedSources.foreach: (label, source, traitName, reason) =>
    test(s"rejects $label") {
      assertRejected(decodeEither(source, traitName), traitName, reason)
    }

  test("rejects non-method and malformed normalized evidence fail closed") {
    val (classView, bodyView) = decodeViews(
      """trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "Curried"
    )
    val member = bodyView.members.head
    val method = member.method.getOrElse(fail("missing normalized method"))
    val firstClause = method.parameterClauses.head
    val first = firstClause.parameters.head
    val secondClause = method.parameterClauses(1)
    val second = secondClause.parameters.head

    val malformed = List(
      (
        member.copy(kind = ExpansionTargetBodyView.DirectMemberKind.Val),
        "direct body member at index 0 must be a curried method; found val"
      ),
      (
        member.copy(method = None),
        "direct body member at index 0 must provide normalized method evidence"
      ),
      (
        member.copy(method = Some(method.copy(name = "<error>"))),
        "direct curried method must have an available normalized name"
      ),
      (
        member.copy(
          method = Some(
            method.copy(
              parameterClauses = firstClause.copy(
                parameters = List(first.copy(name = "<unknown>"))
              ) :: method.parameterClauses.tail
            )
          )
        ),
        "curried method `combine` first parameter `<unknown>` must be named, ordinary, non-defaulted, and unmodified"
      ),
      (
        member.copy(
          method = Some(
            method.copy(
              parameterClauses = firstClause.copy(
                parameters = List(first.copy(isVal = true))
              ) :: method.parameterClauses.tail
            )
          )
        ),
        "curried method `combine` first parameter `a` must be named, ordinary, non-defaulted, and unmodified"
      ),
      (
        member.copy(
          method = Some(
            method.copy(
              parameterClauses = firstClause :: List(
                secondClause.copy(parameters = List(second.copy(isVar = true)))
              )
            )
          )
        ),
        "curried method `combine` second parameter `b` must be named, ordinary, non-defaulted, and unmodified"
      )
    )

    malformed.foreach: (value, reason) =>
      assertRejected(
        InstanceCurriedMethodSourceShapeDecoder.decode(
          classView,
          bodyView.copy(members = List(value))
        ),
        "Curried",
        reason
      )
  }

  private def assertRejected(
      decoded: Either[
        paradise3.api.ExpansionDiagnostic,
        InstanceCurriedMethodSourceShapeDecoder.SourceShape
      ],
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
  ): InstanceCurriedMethodSourceShapeDecoder.SourceShape =
    decodeEither(source, traitName).fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(source: String, traitName: String) =
    val (classView, bodyView) = decodeViews(source, traitName)
    InstanceCurriedMethodSourceShapeDecoder.decode(classView, bodyView)

  private def decodeViews(
      source: String,
      traitName: String
  ): (ExpansionTargetView, ExpansionTargetBodyView) =
    val unit = CompilationUnit(s"${traitName}CurriedInstance.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef for $traitName in $other")
    val classView = ExpansionTargetView.decode(primary).fold(d => fail(d.message), identity)
    val bodyView = ExpansionTargetBodyView.decode(primary).fold(d => fail(d.message), identity)
    (classView, bodyView)
