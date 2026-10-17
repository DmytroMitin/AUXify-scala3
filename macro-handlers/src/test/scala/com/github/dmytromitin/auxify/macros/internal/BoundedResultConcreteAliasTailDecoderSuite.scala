package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetTypeStructureView}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape

class BoundedResultConcreteAliasTailDecoderSuite extends munit.FunSuite:
  test("admits source-ordered aliases after a historically positioned result and interleaved methods") {
    val (body, structure) = decoded(
      """trait RichAdd[N <: Nat, M <: Nat]:
        |  def before(n: N): N = n
        |  type Out <: Nat
        |  type Left = N
        |  def apply(n: N, m: M): Out
        |  type Right = M
        |""".stripMargin
    )

    assertEquals(structure.directTypeMembers.map(_.bodyIndex), List(1, 2, 4))
    assert(ApplyFullShapeDecoder.decode("RichAdd", body, structure).isRight)
    val aux = AuxSourceShapeDecoder.decode("RichAdd", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.resultTypeMemberName, "Out")
    assertEquals(aux.generatedResultParameterName, "Out0")
  }

  test("freshens the generated result binder around alias names in the type namespace") {
    val (body, structure) = decoded(
      """trait Fresh[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Out0 = N
        |  def Out0(n: N): N = n
        |  type Out1 = M
        |""".stripMargin
    )

    val aux = AuxSourceShapeDecoder.decode("Fresh", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.generatedResultParameterName, "Out2")

    val (termBody, termStructure) = decoded(
      """trait TermOnly[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  def Out0(n: N): N = n
        |  type Left = N
        |""".stripMargin
    )
    val termOnly = AuxSourceShapeDecoder.decode("TermOnly", termBody, termStructure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(termOnly.generatedResultParameterName, "Out0")
  }

  private val rejectedSources = List(
    "additional abstract result" -> (
      "type Carry <: Nat",
      "additional direct type member `Carry` must be a concrete alias, found abstract bounds"
    ),
    "additional unbounded abstract member" -> (
      "type Carry",
      "additional direct type member `Carry` must be a concrete alias, found abstract bounds"
    ),
    "result alias before the abstract result" -> (
      "type Left = N\n  type Out <: Nat",
      "result type member `Left` must be abstract bounds, found alias"
    ),
    "alias of an unrelated named type" -> (
      "type Copy = Nat",
      "concrete bounded-result alias `Copy` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
    ),
    "qualified alias" -> (
      "type Copy = domain.Value",
      "concrete bounded-result alias `Copy` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
    ),
    "applied alias" -> (
      "type Copy = Box[N]",
      "concrete bounded-result alias `Copy` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
    ),
    "function alias" -> (
      "type Copy = N => M",
      "concrete bounded-result alias `Copy` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
    ),
    "polymorphic alias" -> (
      "type Copy[X] = N",
      "concrete bounded-result alias `Copy` must not declare type parameters"
    ),
    "private alias" -> (
      "private type Copy = N",
      "concrete bounded-result alias `Copy` must be public, unannotated, and free of unsupported modifiers"
    ),
    "protected alias" -> (
      "protected type Copy = N",
      "concrete bounded-result alias `Copy` must be public, unannotated, and free of unsupported modifiers"
    ),
    "annotated alias" -> (
      "@deprecated type Copy = N",
      "concrete bounded-result alias `Copy` must be public, unannotated, and free of unsupported modifiers"
    ),
    "infix alias" -> (
      "infix type Copy = N",
      "concrete bounded-result alias `Copy` must be public, unannotated, and free of unsupported modifiers"
    )
  )

  rejectedSources.foreach: (label, row) =>
    val (tail, reason) = row
    test(s"rejects $label through the shared aux and apply policy") {
      val source =
        if label == "result alias before the abstract result" then
          s"""trait Rejected[N <: Nat, M <: Nat]:
             |  $tail
             |""".stripMargin
        else
          s"""trait Rejected[N <: Nat, M <: Nat]:
             |  type Out <: Nat
             |  $tail
             |""".stripMargin
      val (body, structure) = decoded(source)
      assertSharedRejection(body, structure, reason)
    }

  test("rejects bounded and missing-binder aliases from normalized evidence") {
    val (body, structure) = decoded(
      """trait Rejected[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = N
        |""".stripMargin
    )
    val alias = structure.directTypeMembers(1)

    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(structure.directTypeMembers.head, alias.copy(lowerBound = paradise3.api.ExpansionTargetTypeStructureView.Bound.Present(alias.aliasTarget.get)))),
      "concrete bounded-result alias `Copy` must not declare lower or upper bounds"
    )
    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(structure.directTypeMembers.head, alias.copy(aliasTarget = Some(DirectTypeShape.EnclosingTypeParameter("Missing", alias.pos))))),
      "concrete bounded-result alias `Copy` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
    )
  }

  test("rejects missing duplicate wrong-index reordered and truncated normalized records") {
    val (body, structure) = decoded(
      """trait Corrupt[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  def apply(n: N, m: M): Out
        |  type Left = N
        |  type Right = M
        |""".stripMargin
    )
    val result :: left :: right :: Nil = structure.directTypeMembers: @unchecked

    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(result, left)),
      "direct body type member at index 3 must provide exactly one normalized direct type record; found 0"
    )
    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(result, left, left.copy(name = "Duplicate"), right)),
      "direct body type member at index 2 must provide exactly one normalized direct type record; found 2"
    )
    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(result, left.copy(bodyIndex = 1), right)),
      "direct type member `Left` at body index 1 must match an actual direct type member"
    )
    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(result, right, left)),
      "normalized direct type-member records must preserve source body order; expected [0, 2, 3], found [0, 3, 2]"
    )
    assertSharedRejection(
      body.copy(members = body.members.dropRight(1)),
      structure,
      "direct type member `Right` references invalid body index 3"
    )
  }

  private def assertSharedRejection(
      body: ExpansionTargetBodyView,
      structure: ExpansionTargetTypeStructureView,
      reason: String
  ): Unit =
    val apply = ApplyFullShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("full apply unexpectedly admitted the shape"))
    val aux = AuxSourceShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("aux unexpectedly admitted the shape"))
    assertEquals(apply.message, s"unsupported full @apply source shape for `Rejected`: $reason")
    assertEquals(aux.message, s"unsupported @aux source shape for `Rejected`: $reason")

  private def decoded(
      source: String
  ): (ExpansionTargetBodyView, ExpansionTargetTypeStructureView) =
    val unit = CompilationUnit("BoundedResultConcreteAliasTailDecoderFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val body = ExpansionTargetBodyView.decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val structure = ExpansionTargetTypeStructureView.decode(primary)
      .fold(diagnostic => fail(diagnostic.message), identity)
    (body, structure)
