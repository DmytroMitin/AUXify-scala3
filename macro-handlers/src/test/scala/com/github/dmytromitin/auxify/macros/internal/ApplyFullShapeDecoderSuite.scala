package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.AnnotatedClassTypeStructureView

class ApplyFullShapeDecoderSuite extends munit.FunSuite:
  test("decodes independently renamed full apply semantic names") {
    val decoded = decode(
      """trait Combine[Left <: Natural, Right <: Natural]:
        |  type Result <: Natural
        |  def combine(left: Left, right: Right): Result
        |""".stripMargin,
      "Combine"
    )

    assertEquals(
      decoded,
      ApplyDefinitionBuilder.FullShape(
        typeClassName = "Combine",
        firstTypeParameterName = "Left",
        secondTypeParameterName = "Right",
        upperBoundTypeName = "Natural",
        resultTypeMemberName = "Result"
      )
    )
  }

  test("rejects mismatched enclosing upper bounds") {
    assertRejected(
      """trait MismatchedBounds[N <: Nat, M <: Other]:
        |  type Out <: Nat
        |""".stripMargin,
      "MismatchedBounds",
      "enclosing type-parameter upper bounds must be the same named type"
    )
  }

  test("rejects a full shape without exactly two enclosing type parameters") {
    assertRejected(
      """trait OneParameter[N <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "OneParameter",
      "requires exactly two enclosing type parameters; found 1"
    )
  }

  test("rejects a variant enclosing type parameter") {
    assertRejected(
      """trait VariantResult[+N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "VariantResult",
      "enclosing type parameter `N` must be invariant"
    )
  }

  test("rejects an enclosing type parameter with a lower bound") {
    assertRejected(
      """trait LowerBoundedInput[N >: Nothing <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "LowerBoundedInput",
      "enclosing type parameter `N` must not define a lower bound"
    )
  }

  test("rejects an enclosing type parameter with a context bound") {
    assertRejected(
      """trait ContextBoundedInput[N <: Nat : Ordering, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "ContextBoundedInput",
      "enclosing type parameter `N` must not define context bounds"
    )
  }

  test("rejects an alias result member") {
    assertRejected(
      """trait AliasResult[N <: Nat, M <: Nat]:
        |  type Out = Nat
        |""".stripMargin,
      "AliasResult",
      "result type member `Out` must be abstract bounds, found alias"
    )
  }

  test("rejects a result member with a lower bound") {
    assertRejected(
      """trait LowerBoundedResult[N <: Nat, M <: Nat]:
        |  type Out >: Nothing <: Nat
        |""".stripMargin,
      "LowerBoundedResult",
      "result type member `Out` must not define a lower bound"
    )
  }

  test("rejects a result upper bound that differs from the enclosing bound") {
    assertRejected(
      """trait MismatchedResult[N <: Nat, M <: Nat]:
        |  type Out <: Other
        |""".stripMargin,
      "MismatchedResult",
      "result type member `Out` upper bound must match enclosing bound `Nat`"
    )
  }

  test("rejects more than one direct type-member candidate") {
    assertRejected(
      """trait MultipleResults[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Extra <: Nat
        |""".stripMargin,
      "MultipleResults",
      "requires exactly one direct type member; found 2"
    )
  }

  test("rejects a polymorphic result type member") {
    assertRejected(
      """trait PolymorphicResult[N <: Nat, M <: Nat]:
        |  type Out[X] <: Nat
        |""".stripMargin,
      "PolymorphicResult",
      "result type member `Out` must not declare type parameters"
    )
  }

  test("rejects a modifier-bearing result type member") {
    assertRejected(
      """trait ProtectedResult[N <: Nat, M <: Nat]:
        |  protected type Out <: Nat
        |""".stripMargin,
      "ProtectedResult",
      "result type member `Out` must be public, unannotated, and free of unsupported modifiers"
    )
  }

  test("rejects a present but unsupported normalized enclosing bound") {
    assertRejected(
      """trait AppliedBounds[N <: Box[N], M <: Box[M]]:
        |  type Out <: Nat
        |""".stripMargin,
      "AppliedBounds",
      "enclosing type-parameter upper bounds must be unqualified named types"
    )
  }

  private def assertRejected(
      source: String,
      typeClassName: String,
      reason: String
  ): Unit =
    val message = decodeEither(source, typeClassName)
      .left
      .toOption
      .getOrElse(fail(s"$typeClassName unexpectedly decoded"))
      .message
    assertEquals(
      message,
      s"unsupported full @apply source shape for `$typeClassName`: $reason"
    )

  private def decode(
      source: String,
      typeClassName: String
  ): ApplyDefinitionBuilder.FullShape =
    decodeEither(source, typeClassName)
      .fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(
      source: String,
      typeClassName: String
  ) =
    val unit = CompilationUnit(s"${typeClassName}DecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source).parse()
    val primary = parsed match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val structure = AnnotatedClassTypeStructureView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    ApplyFullShapeDecoder.decode(typeClassName, structure)
