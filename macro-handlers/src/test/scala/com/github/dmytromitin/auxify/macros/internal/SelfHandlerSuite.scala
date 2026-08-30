package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome}

import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

class SelfHandlerSuite extends munit.FunSuite:
  test("a deterministic bridge failure becomes one bounded rejection without a partial edit") {
    val source =
      """@current
        |trait Nat:
        |  type Existing = String
        |""".stripMargin
    val unit = CompilationUnit("SelfHandlerFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source).parse()
    val primary = parsed match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val currentAnnotation = Trees.mods(primary).annotations.head
    val input = ExpansionInput(
      "com.github.dmytromitin.auxify.macros.self",
      primary,
      None,
      Set("Nat"),
      Some(currentAnnotation)
    )
    val originalTemplate = primary.rhs

    SelfHandler.expandWithLowering(input): (_, _, _) =>
      Left(
        SelfAbstractTypeMemberPeerBridge.Failure(
          "EXACT_RAW_LOWERING_FAILED",
          "controlled bridge failure"
        )
      )
    match
      case ExpansionOutcome.Rejected(diagnostics, fallback) =>
        assertEquals(diagnostics.map(_.message), List(
          "EXACT_RAW_LOWERING_FAILED: controlled bridge failure"
        ))
        assert(fallback.eq(primary), clue(fallback))
        assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
      case other => fail(s"expected controlled Rejected outcome, found $other")
  }
