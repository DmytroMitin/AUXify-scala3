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

  test("admits source-ordered alias chains rooted independently in both parameters and the result") {
    val (body, structure) = decoded(
      """trait RichAdd[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Left = N
        |  type Copy = Out
        |  type CopyAgain = Copy
        |  type CopyBranch = Copy
        |  def apply(n: N, m: M): Out
        |  type LeftAgain = Left
        |  type FinalCopy = CopyAgain
        |  type Right = M
        |  type RightAgain = Right
        |""".stripMargin
    )

    assertEquals(
      structure.directTypeMembers.map(member => member.name -> member.bodyIndex),
      List(
        "Out" -> 0,
        "Left" -> 1,
        "Copy" -> 2,
        "CopyAgain" -> 3,
        "CopyBranch" -> 4,
        "LeftAgain" -> 6,
        "FinalCopy" -> 7,
        "Right" -> 8,
        "RightAgain" -> 9
      )
    )
    val aux = AuxSourceShapeDecoder.decode("RichAdd", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    val full = ApplyFullShapeDecoder.decode("RichAdd", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.resultTypeMemberName, "Out")
    assertEquals(full.resultTypeMemberName, "Out")
  }

  test("freshens the Aux result binder around a direct result-alias name") {
    val (body, structure) = decoded(
      """trait Fresh[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Out0 = Out
        |  type Out1 = Out0
        |  type Copy = Out1
        |  type Left = N
        |""".stripMargin
    )

    val aux = AuxSourceShapeDecoder.decode("Fresh", body, structure)
      .fold(diagnostic => fail(diagnostic.message), identity)
    assertEquals(aux.generatedResultParameterName, "Out2")
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
    ("unrelated named type", "type Copy = Nat", "Copy"),
    ("missing named type", "type Copy = Missing", "Copy"),
    ("self cycle", "type Copy = Copy", "Copy"),
    ("forward reference", "type Before = After\n  type After = Out", "Before"),
    ("mutual cycle", "type First = Second\n  type Second = First", "First"),
    (
      "invalid root after valid chains",
      "type Copy = Out\n  type CopyAgain = Copy\n  type Broken = Missing",
      "Broken"
    ),
    ("qualified type", "type Copy = Other.Out", "Copy"),
    ("applied type", "type Copy = F[N]", "Copy"),
    ("function type", "type Copy = N => M", "Copy")
  ).foreach: (label, declarations, rejectedName) =>
    test(s"rejects a direct result alias target that is $label") {
      val (body, structure) = decoded(
        s"""trait Rejected[N <: Nat, M <: Nat]:
           |  type Out <: Nat
           |  $declarations
           |""".stripMargin
      )

      val rejected = structure.directTypeMembers
        .find(_.name == rejectedName)
        .getOrElse(fail(s"missing normalized record for $rejectedName"))

      assertSharedRejectionAt(
        body,
        structure,
        s"concrete bounded-result alias `$rejectedName` must target one of the enclosing type parameters `M` or `N`, the abstract result member `Out`, or an earlier validated concrete alias",
        rejected.pos
      )
    }

  test("rejects a duplicate direct type name at the later alias") {
    val (body, structure) = decoded(
      """trait Rejected[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |  type Copy = Out
        |  type Copy = Out
        |""".stripMargin
    )
    val duplicate = structure.directTypeMembers.last

    assertSharedRejectionAt(
      body,
      structure,
      "direct type member name `Copy` must be unique across enclosing parameters and direct type members",
      duplicate.pos
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

  private def assertSharedRejectionAt(
      body: ExpansionTargetBodyView,
      structure: ExpansionTargetTypeStructureView,
      reason: String,
      expectedPosition: dotty.tools.dotc.util.SrcPos
  ): Unit =
    val aux = AuxSourceShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("aux unexpectedly admitted the shape"))
    val full = ApplyFullShapeDecoder.decode("Rejected", body, structure)
      .left.toOption.getOrElse(fail("full apply unexpectedly admitted the shape"))
    assertEquals(aux.message, s"unsupported @aux source shape for `Rejected`: $reason")
    assertEquals(full.message, s"unsupported full @apply source shape for `Rejected`: $reason")
    assertEquals(aux.pos, expectedPosition)
    assertEquals(full.pos, expectedPosition)

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
