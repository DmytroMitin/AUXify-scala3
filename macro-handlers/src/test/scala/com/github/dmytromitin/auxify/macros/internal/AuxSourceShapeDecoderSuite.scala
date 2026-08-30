package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.AnnotatedClassTypeStructureView

class AuxSourceShapeDecoderSuite extends munit.FunSuite:
  test("decodes the canonical Add source shape") {
    assertEquals(
      decode(
        """trait Add[N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |""".stripMargin,
        "Add"
      ),
      AuxSourceShapeDecoder.Shape(
        typeClassName = "Add",
        firstTypeParameterName = "N",
        secondTypeParameterName = "M",
        upperBoundTypeName = "Nat",
        resultTypeMemberName = "Out",
        generatedResultParameterName = "Out0"
      )
    )
  }

  test("derives every semantic name from a coherently renamed source shape") {
    assertEquals(
      decode(
        """trait Combine[Left <: Natural, Right <: Natural]:
          |  type Result <: Natural
          |""".stripMargin,
        "Combine"
      ),
      AuxSourceShapeDecoder.Shape(
        typeClassName = "Combine",
        firstTypeParameterName = "Left",
        secondTypeParameterName = "Right",
        upperBoundTypeName = "Natural",
        resultTypeMemberName = "Result",
        generatedResultParameterName = "Result0"
      )
    )
  }

  test("ignores ordinary methods when selecting the single direct type member") {
    val decoded = decode(
      """trait Add[N <: Nat, M <: Nat]:
        |  def add(left: N, right: M): Out
        |  type Out <: Nat
        |""".stripMargin,
      "Add"
    )

    assertEquals(decoded.resultTypeMemberName, "Out")
    assertEquals(decoded.generatedResultParameterName, "Out0")
  }

  test("increments the generated result parameter past enclosing-name collisions") {
    val decoded = decode(
      """trait Weird[Out0 <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "Weird"
    )

    assertEquals(decoded.generatedResultParameterName, "Out1")
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

  test("rejects a result bound different from the enclosing bound") {
    assertRejected(
      """trait MismatchedResult[N <: Nat, M <: Nat]:
        |  type Out <: Other
        |""".stripMargin,
      "MismatchedResult",
      "result type member `Out` upper bound must match enclosing bound `Nat`"
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

  test("rejects a meaningful result lower bound") {
    assertRejected(
      """trait LowerBoundedResult[N <: Nat, M <: Nat]:
        |  type Out >: Nothing <: Nat
        |""".stripMargin,
      "LowerBoundedResult",
      "result type member `Out` must not define a lower bound"
    )
  }

  test("rejects multiple direct type members") {
    assertRejected(
      """trait MultipleResults[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Extra <: Nat
        |""".stripMargin,
      "MultipleResults",
      "requires exactly one direct type member; found 2"
    )
  }

  test("rejects a polymorphic result member") {
    assertRejected(
      """trait PolymorphicResult[N <: Nat, M <: Nat]:
        |  type Out[X] <: Nat
        |""".stripMargin,
      "PolymorphicResult",
      "result type member `Out` must not declare type parameters"
    )
  }

  test("rejects result-member visibility and annotation modifiers") {
    val rows = List(
      (
        """trait ProtectedResult[N <: Nat, M <: Nat]:
          |  protected type Out <: Nat
          |""".stripMargin,
        "ProtectedResult"
      ),
      (
        """trait AnnotatedResult[N <: Nat, M <: Nat]:
          |  @deprecated type Out <: Nat
          |""".stripMargin,
        "AnnotatedResult"
      )
    )

    rows.foreach: (source, typeClassName) =>
      assertRejected(
        source,
        typeClassName,
        "result type member `Out` must be public, unannotated, and free of unsupported modifiers"
      )
  }

  test("rejects applied qualified and otherwise unsupported bound shapes") {
    val rows = List(
      (
        """trait AppliedBounds[N <: Box[N], M <: Box[M]]:
          |  type Out <: Nat
          |""".stripMargin,
        "AppliedBounds",
        "enclosing type-parameter upper bounds must be unqualified named types"
      ),
      (
        """trait QualifiedBounds[N <: domain.Nat, M <: domain.Nat]:
          |  type Out <: domain.Nat
          |""".stripMargin,
        "QualifiedBounds",
        "enclosing type-parameter upper bounds must be unqualified named types"
      ),
      (
        """trait FunctionResult[N <: Nat, M <: Nat]:
          |  type Out <: (Nat => Nat)
          |""".stripMargin,
        "FunctionResult",
        "result type member `Out` upper bound must be an unqualified named type"
      )
    )

    rows.foreach: (source, typeClassName, reason) =>
      assertRejected(source, typeClassName, reason)
  }

  test("rejects enclosing variance lower bounds and context bounds") {
    val rows = List(
      (
        """trait VariantInput[+N <: Nat, M <: Nat]:
          |  type Out <: Nat
          |""".stripMargin,
        "VariantInput",
        "enclosing type parameter `N` must be invariant"
      ),
      (
        """trait LowerBoundedInput[N >: Nothing <: Nat, M <: Nat]:
          |  type Out <: Nat
          |""".stripMargin,
        "LowerBoundedInput",
        "enclosing type parameter `N` must not define a lower bound"
      ),
      (
        """trait ContextBoundedInput[N <: Nat : Ordering, M <: Nat]:
          |  type Out <: Nat
          |""".stripMargin,
        "ContextBoundedInput",
        "enclosing type parameter `N` must not define context bounds"
      )
    )

    rows.foreach: (source, typeClassName, reason) =>
      assertRejected(source, typeClassName, reason)
  }

  test("rejects the wrong enclosing parameter cardinality") {
    assertRejected(
      """trait OneParameter[N <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      "OneParameter",
      "requires exactly two enclosing type parameters; found 1"
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
      s"unsupported @aux source shape for `$typeClassName`: $reason"
    )

  private def decode(
      source: String,
      typeClassName: String
  ): AuxSourceShapeDecoder.Shape =
    decodeEither(source, typeClassName)
      .fold(diagnostic => fail(diagnostic.message), identity)

  private def decodeEither(
      source: String,
      typeClassName: String
  ) =
    val unit = CompilationUnit(s"${typeClassName}AuxDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source).parse()
    val primary = parsed match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val structure = AnnotatedClassTypeStructureView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    AuxSourceShapeDecoder.decode(typeClassName, structure)
