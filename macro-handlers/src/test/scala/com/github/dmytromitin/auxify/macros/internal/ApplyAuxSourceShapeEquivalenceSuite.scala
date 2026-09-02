package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{AnnotatedClassTypeStructureView, ExpansionDiagnostic}

class ApplyAuxSourceShapeEquivalenceSuite extends munit.FunSuite:
  private final case class CommonFacts(
      typeClassName: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundTypeName: String,
      resultTypeMemberName: String
  )

  private final case class Row(
      name: String,
      source: String,
      expectedFacts: Option[CommonFacts] = None,
      expectedReason: Option[String] = None,
      expectedGeneratedResultParameterName: Option[String] = None
  )

  private val rows = List(
    Row(
      "canonical",
      """trait Add[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedFacts = Some(CommonFacts("Add", "N", "M", "Nat", "Out")),
      expectedGeneratedResultParameterName = Some("Out0")
    ),
    Row(
      "coherently renamed with an ordinary method",
      """trait Combine[Left <: Natural, Right <: Natural]:
        |  def combine(left: Left, right: Right): Result
        |  type Result <: Natural
        |""".stripMargin,
      expectedFacts = Some(
        CommonFacts("Combine", "Left", "Right", "Natural", "Result")
      ),
      expectedGeneratedResultParameterName = Some("Result0")
    ),
    Row(
      "generated aux name collision remains feature-specific",
      """trait Weird[Out0 <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedFacts = Some(CommonFacts("Weird", "Out0", "M", "Nat", "Out")),
      expectedGeneratedResultParameterName = Some("Out1")
    ),
    Row(
      "wrong enclosing cardinality",
      """trait OneParameter[N <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some("requires exactly two enclosing type parameters; found 1")
    ),
    Row(
      "first enclosing variance",
      """trait VariantInput[+N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some("enclosing type parameter `N` must be invariant")
    ),
    Row(
      "second enclosing variance preserves source order",
      """trait SecondVariant[N <: Nat, -M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some("enclosing type parameter `M` must be invariant")
    ),
    Row(
      "enclosing lower bound",
      """trait LowerBoundedInput[N >: Nothing <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some("enclosing type parameter `N` must not define a lower bound")
    ),
    Row(
      "enclosing context bound",
      """trait ContextBoundedInput[N <: Nat : Ordering, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some("enclosing type parameter `N` must not define context bounds")
    ),
    Row(
      "applied enclosing upper bound",
      """trait AppliedBounds[N <: Box[N], M <: Box[M]]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some(
        "enclosing type-parameter upper bounds must be unqualified named types"
      )
    ),
    Row(
      "qualified enclosing upper bound",
      """trait QualifiedBounds[N <: domain.Nat, M <: domain.Nat]:
        |  type Out <: domain.Nat
        |""".stripMargin,
      expectedReason = Some(
        "enclosing type-parameter upper bounds must be unqualified named types"
      )
    ),
    Row(
      "mismatched enclosing upper bounds",
      """trait MismatchedBounds[N <: Nat, M <: Other]:
        |  type Out <: Nat
        |""".stripMargin,
      expectedReason = Some(
        "enclosing type-parameter upper bounds must be the same named type"
      )
    ),
    Row(
      "no direct type member",
      """trait MissingResult[N <: Nat, M <: Nat]:
        |  def value: N
        |""".stripMargin,
      expectedReason = Some("requires exactly one direct type member; found 0")
    ),
    Row(
      "multiple direct type members preserve direct-member filtering",
      """trait MultipleResults[N <: Nat, M <: Nat]:
        |  def value: N
        |  type Out <: Nat
        |  type Extra <: Nat
        |""".stripMargin,
      expectedReason = Some("requires exactly one direct type member; found 2")
    ),
    Row(
      "alias member kind and alias target",
      """trait AliasResult[N <: Nat, M <: Nat]:
        |  type Out = Nat
        |""".stripMargin,
      expectedReason = Some("result type member `Out` must be abstract bounds, found alias")
    ),
    Row(
      "member type parameters",
      """trait PolymorphicResult[N <: Nat, M <: Nat]:
        |  type Out[X] <: Nat
        |""".stripMargin,
      expectedReason = Some("result type member `Out` must not declare type parameters")
    ),
    Row(
      "member lower bound",
      """trait LowerBoundedResult[N <: Nat, M <: Nat]:
        |  type Out >: Nothing <: Nat
        |""".stripMargin,
      expectedReason = Some("result type member `Out` must not define a lower bound")
    ),
    Row(
      "member visibility",
      """trait ProtectedResult[N <: Nat, M <: Nat]:
        |  protected type Out <: Nat
        |""".stripMargin,
      expectedReason = Some(
        "result type member `Out` must be public, unannotated, and free of unsupported modifiers"
      )
    ),
    Row(
      "member annotation",
      """trait AnnotatedResult[N <: Nat, M <: Nat]:
        |  @deprecated type Out <: Nat
        |""".stripMargin,
      expectedReason = Some(
        "result type member `Out` must be public, unannotated, and free of unsupported modifiers"
      )
    ),
    Row(
      "unsupported result upper-bound shape",
      """trait FunctionResult[N <: Nat, M <: Nat]:
        |  type Out <: (Nat => Nat)
        |""".stripMargin,
      expectedReason = Some(
        "result type member `Out` upper bound must be an unqualified named type"
      )
    ),
    Row(
      "result and enclosing bound mismatch",
      """trait MismatchedResult[N <: Nat, M <: Nat]:
        |  type Out <: Other
        |""".stripMargin,
      expectedReason = Some(
        "result type member `Out` upper bound must match enclosing bound `Nat`"
      )
    )
  )

  rows.foreach: row =>
    test(s"current decoders are equivalent for ${row.name}") {
      val (typeClassName, structure) = decodeStructure(row.source)
      val applyResult = ApplyFullShapeDecoder.decode(typeClassName, structure)
      val auxResult = AuxSourceShapeDecoder.decode(typeClassName, structure)

      (applyResult, auxResult) match
        case (Right(applyShape), Right(auxShape)) =>
          val applyFacts = CommonFacts(
            applyShape.typeClassName,
            applyShape.firstTypeParameterName,
            applyShape.secondTypeParameterName,
            applyShape.upperBoundTypeName,
            applyShape.resultTypeMemberName
          )
          val auxFacts = CommonFacts(
            auxShape.typeClassName,
            auxShape.firstTypeParameterName,
            auxShape.secondTypeParameterName,
            auxShape.upperBoundTypeName,
            auxShape.resultTypeMemberName
          )
          assertEquals(applyFacts, row.expectedFacts.getOrElse(fail("expected rejection")))
          assertEquals(auxFacts, applyFacts)
          assertEquals(
            Some(auxShape.generatedResultParameterName),
            row.expectedGeneratedResultParameterName
          )
        case (Left(applyDiagnostic), Left(auxDiagnostic)) =>
          val reason = row.expectedReason.getOrElse(fail("expected admission"))
          assertEquals(
            applyDiagnostic.message,
            s"unsupported full @apply source shape for `$typeClassName`: $reason"
          )
          assertEquals(
            auxDiagnostic.message,
            s"unsupported @aux source shape for `$typeClassName`: $reason"
          )
          assertEquals(applyDiagnostic.pos, auxDiagnostic.pos)
        case other =>
          fail(s"decoder admission diverged for ${row.name}: $other")
    }

  test("shared recognition exposes only common source facts") {
    val (typeClassName, structure) = decodeStructure(
      """trait Weird[Out0 <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin
    )

    assertEquals(
      BoundedResultTypeClassShapeDecoder.decode(typeClassName, structure),
      Right(
        BoundedResultTypeClassShape(
          typeClassName = "Weird",
          firstTypeParameterName = "Out0",
          secondTypeParameterName = "M",
          upperBoundTypeName = "Nat",
          resultTypeMemberName = "Out"
        )
      )
    )
  }

  private def decodeStructure(
      source: String
  ): (String, AnnotatedClassTypeStructureView) =
    val unit = CompilationUnit("ApplyAuxShapeEquivalenceFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source).parse()
    val primary = parsed match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val structure = AnnotatedClassTypeStructureView
      .decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (primary.name.show, structure)
