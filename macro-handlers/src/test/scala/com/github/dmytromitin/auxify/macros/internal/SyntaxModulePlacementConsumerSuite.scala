package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.*
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{
  DefinitionPlacement,
  ExpansionEdit,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetKind
}
import paradise3.api.helpers.{
  ExpansionTransforms,
  MemberConflictPolicy,
  MissingCompanionPolicy
}

/** Test-only consumption of Macro-Paradise input 044.
  *
  * The raw `syntax` ModuleDef below is deliberately only a placement fixture.
  * Quasiquotes input 045 remains the production owner of exact extension-method
  * authoring and lowering.
  */
class SyntaxModulePlacementConsumerSuite extends munit.FunSuite:
  test("missing companion receives the exact supplied syntax ModuleDef") {
    val fixture = parsedFixture()
    given Context = fixture.context

    val input = fixture.input(None)
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val companion = output.companion.getOrElse(fail("missing generated companion"))
    assertEquals(companion.name.toString, "Show")
    assertEquals(companion.impl.body, List(fixture.generatedModule))
    assert(companion.impl.body.head.eq(fixture.generatedModule), clue(companion.impl.body))
  }

  test("existing companion preserves exact shape, order, and identities before appending the supplied module") {
    val fixture = parsedFixture("val before: Int = 1\nval after: Int = 2")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val existingTemplate = existing.impl
    val existingBody = existingTemplate.body
    val existingMods = Trees.mods(existing)

    val input = fixture.input(Some(existing))
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val merged = output.companion.getOrElse(fail("missing merged companion"))
    val mergedTemplate = merged.impl
    assert(!merged.eq(existing), clue(merged))
    assertEquals(mergedTemplate.body.size, existingBody.size + 1)
    assert(
      mergedTemplate.body.take(existingBody.size).zip(existingBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(mergedTemplate.body)
    )
    assert(mergedTemplate.body.last.eq(fixture.generatedModule), clue(mergedTemplate.body))
    assert(mergedTemplate.constr.eq(existingTemplate.constr), clue(mergedTemplate.constr))
    assertEquals(mergedTemplate.parentsOrDerived, existingTemplate.parentsOrDerived)
    assertEquals(mergedTemplate.derived, existingTemplate.derived)
    assert(mergedTemplate.self.eq(existingTemplate.self), clue(mergedTemplate.self))
    assertEquals(Trees.mods(merged), existingMods)
    assertEquals(merged.sourcePos, existing.sourcePos)
    assertEquals(mergedTemplate.sourcePos, existingTemplate.sourcePos)
  }

  test("direct object, def, and val syntax members are term conflicts") {
    List(
      "object syntax:\n  val existing: Int = 1",
      "def syntax: Int = 1",
      "val syntax: Int = 1"
    ).foreach { conflictingDefinition =>
      val fixture = parsedFixture(conflictingDefinition)
      given Context = fixture.context
      val existing = fixture.companion.getOrElse(fail("missing existing companion"))

      val input = fixture.input(Some(existing))
      val output = structured(input):
        place(
          input,
          fixture.generatedModule,
          MemberConflictPolicy.PreserveExisting
        )

      assert(output.companion.getOrElse(fail("missing companion")).eq(existing), clue(conflictingDefinition))
      assertEquals(directTermMembersNamed(existing, "syntax").size, 1)
    }
  }

  test("the generic member policy preserves term and type namespace separation") {
    val fixture = parsedFixture("type syntax = String")
    given Context = fixture.context

    val input = fixture.input(fixture.companion)
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val merged = output.companion.getOrElse(fail("missing merged companion"))
    assertEquals(directTermMembersNamed(merged, "syntax"), List(fixture.generatedModule))
    assert(
      merged.impl.body.exists {
        case value: TypeDef => value.name.toString == "syntax"
        case _ => false
      },
      clue(merged.impl.body)
    )
  }

  test("a nested same-name module is not a direct conflict") {
    val fixture = parsedFixture("object Nested:\n  object syntax:\n    val existing: Int = 1")
    given Context = fixture.context

    val input = fixture.input(fixture.companion)
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val merged = output.companion.getOrElse(fail("missing merged companion"))
    assertEquals(directTermMembersNamed(merged, "syntax"), List(fixture.generatedModule))
  }

  test("PreserveExisting returns the exact conflicting companion unchanged") {
    val fixture = parsedFixture("object syntax:\n  val existing: Int = 1")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val originalBody = existing.impl.body

    val input = fixture.input(Some(existing))
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val preserved = output.companion.getOrElse(fail("missing companion"))
    assert(preserved.eq(existing), clue(preserved))
    assert(
      preserved.impl.body.zip(originalBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(preserved.impl.body)
    )
  }

  test("Reject is atomic and reports one deterministic module diagnostic") {
    val fixture = parsedFixture("val syntax: Int = 1")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val originalBody = existing.impl.body

    val input = fixture.input(Some(existing))
    place(
      input,
      fixture.generatedModule,
      MemberConflictPolicy.Reject
    ) match
      case ExpansionOutcome.Rejected(diagnostics) =>
        assertEquals(diagnostics.size, 1)
        assertEquals(
          diagnostics.head.message,
          "generated member `syntax` conflicts with a direct member of `Show`"
        )
        assertEquals(diagnostics.head.pos, fixture.currentAnnotation.sourcePos)
      case other => fail(s"expected Rejected, found $other")

    assert(
      existing.impl.body.zip(originalBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(existing.impl.body)
    )
    assertEquals(directTermMembersNamed(existing, "syntax").size, 1)
  }

  test("placement preserves the supplied ModuleDef, template, and body identities opaquely") {
    val fixture = parsedFixture("val preserved: Int = 1")
    given Context = fixture.context
    val generatedTemplate = fixture.generatedModule.impl
    val generatedBody = generatedTemplate.body

    val input = fixture.input(fixture.companion)
    val output = structured(input):
      place(
        input,
        fixture.generatedModule,
        MemberConflictPolicy.PreserveExisting
      )

    val inserted = directTermMembersNamed(
      output.companion.getOrElse(fail("missing companion")),
      "syntax"
    ).headOption.getOrElse(fail("missing inserted module"))
    assert(inserted.eq(fixture.generatedModule), clue(inserted))
    val insertedModule = inserted.asInstanceOf[ModuleDef]
    assert(insertedModule.impl.eq(generatedTemplate), clue(insertedModule.impl))
    assert(
      insertedModule.impl.body.zip(generatedBody).forall {
        case (actual, expected) => actual.eq(expected)
      },
      clue(insertedModule.impl.body)
    )
  }

  private final case class Fixture(
      primary: TypeDef,
      companion: Option[ModuleDef],
      generatedModule: ModuleDef,
      currentAnnotation: Tree,
      context: Context
  ):
    def input(existingCompanion: Option[ModuleDef]): ExpansionInput =
      paradise3.api.ExpansionInputTestFactory(
        "current",
        primary,
        existingCompanion,
        Set("Show", "GeneratedModuleOwner"),
        Some(currentAnnotation)
      )(using context)

  private def parsedFixture(existingBody: String = ""): Fixture =
    val companion =
      if existingBody.isEmpty then ""
      else
        s"""
           |object Show extends Serializable:
           |  self =>
           |${indent(existingBody)}
           |""".stripMargin
    val source =
      s"""@current @later
         |trait Show[A]:
         |  def show(value: A): String
         |$companion
         |object GeneratedModuleOwner:
         |  object syntax:
         |    val marker: String = "placed"
         |""".stripMargin
    val unit = CompilationUnit("SyntaxModulePlacementFixture.scala", source)
    val context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source)(using context).parse()
    val stats = parsed match
      case PackageDef(_, values) => values
      case tree => List(tree)
    given Context = context
    val primary = typeDefNamed(stats, "Show")
    val existingCompanion = stats.collectFirst {
      case value: ModuleDef if value.name.toString == "Show" => value
    }
    val owner = stats.collectFirst {
      case value: ModuleDef if value.name.toString == "GeneratedModuleOwner" => value
    }.getOrElse(fail(s"missing generated module owner in $stats"))
    val generatedModule = owner.impl.body.collectFirst {
      case value: ModuleDef if value.name.toString == "syntax" => value
    }.getOrElse(fail(s"missing generated module in ${owner.impl.body}"))
    val currentAnnotation = Trees.mods(primary).annotations.headOption
      .getOrElse(fail("missing current annotation"))
    Fixture(primary, existingCompanion, generatedModule, currentAnnotation, context)

  private def directTermMembersNamed(
      companion: ModuleDef,
      name: String
  )(using Context): List[MemberDef] =
    companion.impl.body.collect {
      case member: MemberDef if member.name == termName(name) => member
    }

  private def typeDefNamed(stats: List[Tree], name: String): TypeDef =
    stats.collectFirst {
      case value: TypeDef if value.name.toString == name => value
    }.getOrElse(fail(s"missing TypeDef $name in $stats"))

  private def indent(value: String): String =
    value.linesIterator.map(line => s"  $line").mkString("\n")

  private def place(
      input: ExpansionInput,
      member: Tree,
      policy: MemberConflictPolicy
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      ExpansionEdit.start(input).flatMap(
        ExpansionTransforms.placeMemberInCompanion(
          member,
          MissingCompanionPolicy.Create(
            ExpansionTargetKind.Object,
            DefinitionPlacement.AfterPrimary
          ),
          policy
        )
      )

  private def structured(
      input: ExpansionInput
  )(outcome: ExpansionOutcome)(using Context): StructuredExpansionOutput =
    outcome match
      case ExpansionOutcome.Structured(changes) =>
        StructuredOutcomeTestSupport.materialize(input, changes)
      case other => fail(s"expected Structured, found $other")
