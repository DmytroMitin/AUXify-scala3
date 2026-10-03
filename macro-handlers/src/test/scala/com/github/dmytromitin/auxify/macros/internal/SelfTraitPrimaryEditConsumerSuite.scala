package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{ExpansionInput, ExpansionOutcome}
import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

class SelfTraitPrimaryEditConsumerSuite extends munit.FunSuite:
  test("anonymous self preparation installs a deterministic alias and preserves original members") {
    val fixture = parsedFixture(
      """trait Nat:
        |  type Existing = String
        |  def existing: String = "anonymous"
        |""".stripMargin
    )
    given Context = fixture.context
    val input = fixture.input
    val originalTemplate = fixture.primary.rhs.asInstanceOf[Template]
    val originalBody = originalTemplate.body
    var selectedAlias = ""

    val output = structured(input):
      SelfHandler.expandWithLowering(input): (traitName, alias, context) =>
        selectedAlias = alias
        SelfDefinitionBuilder.lower(traitName, alias)(using context)

    val rewrittenTemplate = output.primary.rhs.asInstanceOf[Template]
    assertEquals(selectedAlias, "self")
    assertEquals(rewrittenTemplate.self.name.toString, "self")
    assert(
      rewrittenTemplate.body.take(originalBody.size).zip(originalBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(rewrittenTemplate.body)
    )
    assertEquals(rewrittenTemplate.body.last.asInstanceOf[TypeDef].name.toString, "Self")
    assertEquals(
      Trees.mods(output.primary).annotations,
      Trees.mods(fixture.primary).annotations
    )
  }

  test("an existing usable named self is preserved by exact identity") {
    val fixture = parsedFixture(
      """trait Nat:
        |  stable =>
        |  type Existing = String
        |""".stripMargin
    )
    given Context = fixture.context
    val input = fixture.input
    val originalSelf = fixture.primary.rhs.asInstanceOf[Template].self
    var selectedAlias = ""

    val output = structured(input):
      SelfHandler.expandWithLowering(input): (traitName, alias, context) =>
        selectedAlias = alias
        SelfDefinitionBuilder.lower(traitName, alias)(using context)

    val rewritten = output.primary.rhs.asInstanceOf[Template]
    assertEquals(selectedAlias, "stable")
    assert(rewritten.self.eq(originalSelf), clue(rewritten.self))
    assertEquals(rewritten.body.last.asInstanceOf[TypeDef].name.toString, "Self")
  }

  test("generated alias selection skips direct term names but not same-spelling type names") {
    val fixture = parsedFixture(
      """trait Nat:
        |  val self: Int = 1
        |  def self$1: Int = 2
        |  type self = String
        |""".stripMargin
    )
    given Context = fixture.context
    val input = fixture.input
    var selectedAlias = ""

    val output = structured(input):
      SelfHandler.expandWithLowering(input): (traitName, alias, context) =>
        selectedAlias = alias
        SelfDefinitionBuilder.lower(traitName, alias)(using context)

    assertEquals(selectedAlias, "self$2")
    assertEquals(
      output.primary.rhs.asInstanceOf[Template].self.name.toString,
      "self$2"
    )
  }

  test("a direct Self type rejects before lowering and leaves the primary untouched") {
    val fixture = parsedFixture(
      """trait Nat:
        |  type Self = String
        |  val preserved: Int = 1
        |""".stripMargin
    )
    given Context = fixture.context
    val input = fixture.input
    val originalTemplate = fixture.primary.rhs
    var loweringCalls = 0

    SelfHandler.expandWithLowering(input): (_, _, _) =>
      loweringCalls += 1
      fail("lowering must not run after a direct Self conflict")
    match
      case ExpansionOutcome.Rejected(diagnostics) =>
        assertEquals(loweringCalls, 0)
        assertEquals(diagnostics.size, 1)
        assertEquals(
          diagnostics.head.message,
          "trait `Nat` already contains direct type member `Self`; bounded self preparation requires deterministic rejection"
        )
        assert(fixture.primary.rhs.eq(originalTemplate), clue(fixture.primary.rhs))
      case other => fail(s"expected Rejected, found $other")
  }

  test("the migrated handler preserves the plain zero-parameter trait envelope") {
    List(
      "class Nat",
      "sealed trait Nat",
      "trait Nat[A]",
      "trait Nat(val value: Int)"
    ).foreach: definition =>
      val fixture = parsedFixture(definition)
      given Context = fixture.context
      val input = fixture.input
      var loweringCalls = 0

      SelfHandler.expandWithLowering(input): (_, _, _) =>
        loweringCalls += 1
        fail("lowering must not run for an unsupported target")
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(loweringCalls, 0, definition)
          assertEquals(diagnostics.size, 1, definition)
        case other => fail(s"expected Rejected for $definition, found $other")
  }

  test("a deterministic lowering failure rejects without a partial self edit") {
    val fixture = parsedFixture(
      """trait Nat:
        |  type Existing = String
        |""".stripMargin
    )
    given Context = fixture.context
    val input = fixture.input
    val originalTemplate = fixture.primary.rhs

    SelfHandler.expandWithLowering(input): (_, _, _) =>
      Left(
        SelfAbstractTypeMemberPeerBridge.Failure(
          "EXACT_RAW_LOWERING_FAILED",
          "controlled bridge failure"
        )
      )
    match
      case ExpansionOutcome.Rejected(diagnostics) =>
        assertEquals(
          diagnostics.map(_.message),
          List("EXACT_RAW_LOWERING_FAILED: controlled bridge failure")
        )
        assert(fixture.primary.rhs.eq(originalTemplate), clue(fixture.primary.rhs))
      case other => fail(s"expected Rejected, found $other")
  }

  private final case class Fixture(
      primary: TypeDef,
      currentAnnotation: Tree,
      context: Context
  ):
    def input: ExpansionInput =
      paradise3.api.ExpansionInputTestFactory(
        "current",
        primary,
        None,
        Set(primary.name.toString),
        Some(currentAnnotation)
      )(using context)

  private def parsedFixture(primaryDefinition: String): Fixture =
    val source =
      s"""@current @later
         |$primaryDefinition
         |""".stripMargin
    val unit = CompilationUnit("SelfTraitPrimaryEditFixture.scala", source)
    val context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source)(using context).parse()
    val primary = parsed match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef                     => value
      case other                              => fail(s"missing primary TypeDef in $other")
    given Context = context
    val currentAnnotation = Trees.mods(primary).annotations.headOption
      .getOrElse(fail("missing current annotation"))
    Fixture(primary, currentAnnotation, context)

  private def structured(
      input: ExpansionInput
  )(outcome: ExpansionOutcome)(using Context): StructuredExpansionOutput =
    outcome match
      case ExpansionOutcome.Structured(changes) =>
        StructuredOutcomeTestSupport.materialize(input, changes)
      case other => fail(s"expected Structured, found $other")
