package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{ExpansionInput, ExpansionOutcome, ExpansionTargetProfile, StructuredExpansionOutput}
import paradise3.api.helpers.{ExpansionHelpers, SelfAliasOrigin}

/** Test-only consumption of Macro-Paradise input 047.
  *
  * The parsed raw `Self` TypeDefs below are deliberately simple
  * placement/lifecycle fixtures. They do not model or lower AUXify's real
  * `self.type` lower bound or refined `Nat` upper bound; that exact production
  * lowering remains owned by Quasiquotes input 046.
  */
class SelfTraitPrimaryEditConsumerSuite extends munit.FunSuite:
  test("anonymous self preparation installs the prepared alias and preserves the exact supplied and original members") {
    assertEquals(
      ExpansionTargetProfile.PlainZeroParameterTrait.toString,
      "PlainZeroParameterTrait"
    )

    val fixture = parsedFixture(
      """trait Nat:
        |  type Existing = String
        |  def existing: String = "anonymous"
        |""".stripMargin
    )
    given Context = fixture.context
    val originalTemplate = fixture.primary.rhs.asInstanceOf[Template]
    val originalBody = originalTemplate.body
    val originalAnnotations = Trees.mods(fixture.primary).annotations
    var callbackCalls = 0

    val output = structured:
      ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { preparation =>
        callbackCalls += 1
        assertEquals(preparation.selfAliasName, "self")
        assertEquals(preparation.selfAliasOrigin, SelfAliasOrigin.Generated)
        assert(preparation.pos.span.exists, clue(preparation.pos))
        fixture.generatedSelf
      }

    val rewrittenTemplate = output.primary.rhs.asInstanceOf[Template]
    assertEquals(callbackCalls, 1)
    assertEquals(rewrittenTemplate.self.name.toString, "self")
    assert(rewrittenTemplate.body.head.eq(fixture.generatedSelf), clue(rewrittenTemplate.body))
    assert(
      rewrittenTemplate.body.tail.zip(originalBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(rewrittenTemplate.body)
    )
    val remainingAnnotations = Trees.mods(output.primary).annotations
    assertEquals(originalAnnotations.size, 2)
    assert(!remainingAnnotations.exists(_ eq fixture.currentAnnotation))
    assertEquals(remainingAnnotations.size, 1)
    assert(remainingAnnotations.head.eq(originalAnnotations.last), clue(remainingAnnotations))
  }

  test("an existing named self is exposed and preserved by exact identity") {
    val fixture = parsedFixture(
      """trait Nat:
        |  stable =>
        |  type Existing = String
        |""".stripMargin
    )
    given Context = fixture.context
    val originalTemplate = fixture.primary.rhs.asInstanceOf[Template]
    val originalSelf = originalTemplate.self
    var callbackCalls = 0

    val output = structured:
      ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { preparation =>
        callbackCalls += 1
        assertEquals(preparation.selfAliasName, "stable")
        assertEquals(preparation.selfAliasOrigin, SelfAliasOrigin.ExistingNamed)
        assertEquals(preparation.pos, originalSelf.sourcePos)
        fixture.generatedSelf
      }

    val rewrittenTemplate = output.primary.rhs.asInstanceOf[Template]
    assertEquals(callbackCalls, 1)
    assert(rewrittenTemplate.self.eq(originalSelf), clue(rewrittenTemplate.self))
    assert(rewrittenTemplate.body.head.eq(fixture.generatedSelf), clue(rewrittenTemplate.body))
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
    var callbackAlias = ""

    val output = structured:
      ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { preparation =>
        callbackAlias = preparation.selfAliasName
        fixture.generatedSelf
      }

    assertEquals(callbackAlias, "self$2")
    assertEquals(output.primary.rhs.asInstanceOf[Template].self.name.toString, "self$2")
  }

  test("a direct Self type rejects before lowering and returns the untouched primary") {
    val fixture = parsedFixture(
      """trait Nat:
        |  type Self = String
        |  val preserved: Int = 1
        |""".stripMargin
    )
    given Context = fixture.context
    val originalTemplate = fixture.primary.rhs
    val directSelf = originalTemplate.asInstanceOf[Template].body.collectFirst {
      case value: TypeDef if value.name.toString == "Self" => value
    }.getOrElse(fail("missing direct Self"))
    var callbackCalls = 0

    ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { _ =>
      callbackCalls += 1
      fixture.generatedSelf
    } match
      case ExpansionOutcome.Rejected(diagnostics, fallback) =>
        assertEquals(callbackCalls, 0)
        assertEquals(diagnostics.size, 1)
        assertEquals(
          diagnostics.head.message,
          "trait `Nat` already contains direct type member `Self`; bounded self preparation requires deterministic rejection"
        )
        assertEquals(diagnostics.head.pos, directSelf.sourcePos)
        assert(fallback.eq(fixture.primary), clue(fallback))
        assert(fixture.primary.rhs.eq(originalTemplate), clue(fixture.primary.rhs))
      case other => fail(s"expected Rejected, found $other")
  }

  test("bounded wrong target shapes reject before lowering") {
    List(
      "class Nat",
      "sealed trait Nat",
      "trait Nat[A]",
      "trait Nat(val value: Int)",
      "enum Nat:\n  case Zero"
    ).foreach { definition =>
      val fixture = parsedFixture(definition)
      given Context = fixture.context
      var callbackCalls = 0

      ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { _ =>
        callbackCalls += 1
        fixture.generatedSelf
      } match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(callbackCalls, 0, definition)
          assertEquals(diagnostics.size, 1, definition)
          assert(fallback.eq(fixture.primary), definition)
        case other => fail(s"expected Rejected for $definition, found $other")
    }
  }

  test("wrongly named and null callback members reject atomically") {
    val fixture = parsedFixture(
      """trait Nat:
        |  type Existing = String
        |""".stripMargin
    )
    given Context = fixture.context
    val originalTemplate = fixture.primary.rhs

    List(
      "wrong name" -> fixture.generatedOther,
      "null member" -> null.asInstanceOf[TypeDef]
    ).foreach { case (label, generated) =>
      var callbackCalls = 0
      ExpansionHelpers.addPreparedSelfTypeToTrait(fixture.input) { _ =>
        callbackCalls += 1
        generated
      } match
        case ExpansionOutcome.Rejected(diagnostics, fallback) =>
          assertEquals(callbackCalls, 1, label)
          assertEquals(diagnostics.size, 1, label)
          assert(fallback.eq(fixture.primary), label)
          assert(fixture.primary.rhs.eq(originalTemplate), label)
        case other => fail(s"expected Rejected for $label, found $other")
    }
  }

  private final case class Fixture(
      primary: TypeDef,
      generatedSelf: TypeDef,
      generatedOther: TypeDef,
      currentAnnotation: Tree,
      context: Context
  ):
    def input: ExpansionInput =
      ExpansionInput(
        "current",
        primary,
        None,
        Set(primary.name.toString, "GeneratedTypes"),
        Some(currentAnnotation)
      )

  private def parsedFixture(primaryDefinition: String): Fixture =
    val source =
      s"""@current @later
         |$primaryDefinition
         |object GeneratedTypes:
         |  type Self = String
         |  type Other = String
         |""".stripMargin
    val unit = CompilationUnit("SelfTraitPrimaryEditFixture.scala", source)
    val context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source)(using context).parse()
    val stats = parsed match
      case PackageDef(_, values) => values
      case tree => List(tree)
    val primary = stats.collectFirst {
      case value: TypeDef if value.name.toString != "GeneratedTypes" => value
    }.getOrElse(fail(s"missing primary TypeDef in $stats"))
    val generatedOwner = stats.collectFirst {
      case value: ModuleDef if value.name.toString == "GeneratedTypes" => value
    }.getOrElse(fail(s"missing GeneratedTypes in $stats"))
    val generated = generatedOwner.impl.body(using context).collect {
      case value: TypeDef => value.name.toString -> value
    }.toMap
    val currentAnnotation = Trees.mods(primary).annotations.headOption
      .getOrElse(fail("missing current annotation"))
    Fixture(
      primary,
      generated.getOrElse("Self", fail(s"missing generated Self in $generated")),
      generated.getOrElse("Other", fail(s"missing generated Other in $generated")),
      currentAnnotation,
      context
    )

  private def structured(outcome: ExpansionOutcome): StructuredExpansionOutput =
    outcome match
      case ExpansionOutcome.Structured(output) => output
      case other => fail(s"expected Structured, found $other")
