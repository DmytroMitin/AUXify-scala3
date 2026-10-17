package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionTargetBodyView, ExpansionTargetTypeStructureView}
import paradise3.api.ExpansionTargetBodyView.DirectTypeShape

class BoundedResultDirectOutAliasDecoderSuite extends munit.FunSuite:
  test("admits a direct alias to the validated abstract result member") {
    val (body, structure) = decoded(
      """trait RichAdd[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = Out
        |""".stripMargin
    )

    assertEquals(
      structure.directTypeMembers(1).aliasTarget.map {
        case DirectTypeShape.NamedType(name, _) => name
        case other => fail(s"expected named result reference, found $other")
      },
      Some("Out")
    )
    val aux = AuxSourceShapeDecoder.decode("RichAdd", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val full = ApplyFullShapeDecoder.decode("RichAdd", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.resultTypeMemberName, "Out")
    assertEquals(full.resultTypeMemberName, "Out")
  }

  test("admits renamed and repeated direct result aliases mixed with enclosing aliases and methods") {
    val (body, structure) = decoded(
      """trait Combine[L <: Natural, R <: Natural]:
        |  def before(left: L): L = left
        |  type Result <: Natural
        |  type Left = L
        |  type Copy = Result
        |  def apply(left: L, right: R): Result
        |  type OtherCopy = Result
        |  type Right = R
        |""".stripMargin
    )

    assertEquals(structure.directTypeMembers.map(_.bodyIndex), List(1, 2, 3, 5, 6))
    val aux = AuxSourceShapeDecoder.decode("Combine", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val full = ApplyFullShapeDecoder.decode("Combine", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.resultTypeMemberName, "Result")
    assertEquals(full.resultTypeMemberName, "Result")
  }

  test("freshens the Aux result binder around a direct result-alias name") {
    val (body, structure) = decoded(
      """trait Fresh[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Out0 = Out
        |  type Copy = Out
        |  type Left = N
        |""".stripMargin
    )

    val aux = AuxSourceShapeDecoder.decode("Fresh", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.generatedResultParameterName, "Out1")
  }

  test("rejects a normalized type record whose name disagrees with its body member") {
    val (body, structure) = decoded(
      """trait RichAdd[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = Out
        |""".stripMargin
    )
    val tamperedBody = body.copy(
      members = body.members.updated(1, body.members(1).copy(name = "OtherCopy"))
    )

    val rejection = AuxSourceShapeDecoder.decode("RichAdd", tamperedBody, structure)
      .left.toOption.getOrElse(fail("name-tampered normalized evidence was admitted"))
    assertEquals(
      rejection.message,
      "unsupported @aux source shape for `RichAdd`: normalized direct type member `Copy` at body index 1 must match actual direct type member `OtherCopy`"
    )
  }

  test("reports modifier rejection as a bounded-result alias violation") {
    val (body, structure) = decoded(
      """trait RichAdd[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  private type Copy = Out
        |""".stripMargin
    )

    val rejection = AuxSourceShapeDecoder.decode("RichAdd", body, structure)
      .left.toOption.getOrElse(fail("private result alias was admitted"))
    assertEquals(
      rejection.message,
      "unsupported @aux source shape for `RichAdd`: concrete bounded-result alias `Copy` must be public, unannotated, and free of unsupported modifiers"
    )
  }

  List(
    "unrelated named type" -> "Nat",
    "missing named type" -> "Missing",
    "alias chain" -> "Copy",
    "qualified type" -> "Other.Out",
    "applied type" -> "F[N]",
    "function type" -> "N => M"
  ).foreach: (label, target) =>
    test(s"rejects a direct result alias target that is a $label") {
      val declarations =
        if label == "alias chain" then
          "type Copy = Out\n  type Another = Copy"
        else s"type Copy = $target"
      val (body, structure) = decoded(
        s"""trait Rejected[N <: Nat, M <: Nat]:
           |  type Out <: Nat
           |  $declarations
           |""".stripMargin
      )
      val rejectedName = if label == "alias chain" then "Another" else "Copy"

      assertSharedRejection(
        body,
        structure,
        s"concrete bounded-result alias `$rejectedName` must target one of the enclosing type parameters `M` or `N` or the abstract result member `Out`"
      )
    }

  test("keeps a second abstract result member rejected") {
    val (body, structure) = decoded(
      """trait Rejected[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = Out
        |  type Carry <: Nat
        |""".stripMargin
    )

    assertSharedRejection(
      body,
      structure,
      "additional direct type member `Carry` must be a concrete alias, found abstract bounds"
    )
  }

  test("rejects a name-tampered result record as well as an alias record") {
    val (body, structure) = decoded(
      """trait Rejected[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = Out
        |""".stripMargin
    )
    val result :: alias :: Nil = structure.directTypeMembers: @unchecked

    assertSharedRejection(
      body,
      structure.copy(directTypeMembers = List(result.copy(name = "Result"), alias)),
      "normalized direct type member `Result` at body index 0 must match actual direct type member `Out`"
    )
  }

  private def assertSharedRejection(
      body: ExpansionTargetBodyView,
      structure: ExpansionTargetTypeStructureView,
      reason: String
  ): Unit =
    val aux = AuxSourceShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("aux unexpectedly admitted the shape"))
    val full = ApplyFullShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("full apply unexpectedly admitted the shape"))
    assertEquals(aux.message, s"unsupported @aux source shape for `Rejected`: $reason")
    assertEquals(full.message, s"unsupported full @apply source shape for `Rejected`: $reason")

  private def decoded(
      source: String
  ): (ExpansionTargetBodyView, ExpansionTargetTypeStructureView) =
    val unit = CompilationUnit("BoundedResultDirectOutAliasDecoderFixture.scala", source)
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
