package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{
  DefinitionPlacement,
  ExpansionEdit,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetKind,
  ExpansionTargetView
}
import paradise3.api.helpers.{
  ExpansionTransforms,
  MemberConflictPolicy,
  MissingCompanionPolicy
}

/** Test-only consumption of Macro-Paradise input 040.
  *
  * The raw `Aux` TypeDef below is deliberately only a placement fixture.
  * Quasiquotes input 039 remains the production owner of exact neutral-alias
  * validation and lowering.
  */
class AuxPlacementConsumerSuite extends munit.FunSuite:
  test("the current target view exposes the normalized canonical two-upper-bounded trait shape") {
    val canonical = shape(
      """trait Add[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin
    )

    assertEquals(canonical.definitionKind, ExpansionTargetView.DefinitionKind.Trait)
    assertEquals(canonical.typeParameters.map(_.name), List("N", "M"))
    assert(canonical.typeParameters.forall(_.variance == ExpansionTargetView.Variance.Invariant))
    assert(canonical.typeParameters.forall(_.isOrdinaryUpperBounded))
    assert(canonical.typeParameters.forall(!_.isOrdinaryUnbounded))
    assert(canonical.typeParameters.forall(!_.hasContextBounds))
    assertEquals(canonical.constructorClauses, Nil)
  }

  test("the normalized profile evidence distinguishes unbounded, contextual, and constructor shapes") {
    val unbounded = shape("trait Unbounded[N, M <: Nat]")
    assert(unbounded.typeParameters.head.isOrdinaryUnbounded)
    assert(!unbounded.typeParameters.head.isOrdinaryUpperBounded)

    val contextual = shape("trait Contextual[N <: Nat : Ordering, M <: Nat]")
    assert(contextual.typeParameters.head.hasContextBounds)

    val constructed = shape("trait Constructed[N <: Nat, M <: Nat](value: Int)")
    assertEquals(constructed.constructorClauses.map(_.parameters.map(_.name)), List(List("value")))
  }

  test("missing companion is created with the exact supplied TypeDef") {
    val fixture = parsedFixture()
    given Context = fixture.context

    val input = fixture.input(None)
    val output = structured(input):
      place(
        input,
        fixture.generatedType,
        MemberConflictPolicy.PreserveExisting
      )

    val companion = output.companion.getOrElse(fail("missing generated companion"))
    assertEquals(companion.name.toString, "Add")
    assertEquals(directTypesNamed(companion, "Aux"), List(fixture.generatedType))
    assert(companion.impl.body.head.eq(fixture.generatedType), clue(companion.impl.body))
  }

  test("existing companion members remain ordered before the exact supplied TypeDef") {
    val fixture = parsedFixture("val before: Int = 1\nval after: Int = 2")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val originalBody = existing.impl.body

    val input = fixture.input(Some(existing))
    val output = structured(input):
      place(
        input,
        fixture.generatedType,
        MemberConflictPolicy.PreserveExisting
      )

    val merged = output.companion.getOrElse(fail("missing merged companion"))
    assert(
      merged.impl.body.take(originalBody.size).zip(originalBody).forall(_ eq _),
      clue(merged.impl.body)
    )
    assert(merged.impl.body.last.eq(fixture.generatedType), clue(merged.impl.body))
  }

  test("PreserveExisting keeps a direct conflicting type unchanged") {
    val fixture = parsedFixture("type Aux = String")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))

    val input = fixture.input(Some(existing))
    val output = structured(input):
      place(
        input,
        fixture.generatedType,
        MemberConflictPolicy.PreserveExisting
      )

    assert(output.companion.getOrElse(fail("missing companion")).eq(existing), clue(output.companion))
    assertEquals(directTypesNamed(existing, "Aux").size, 1)
  }

  test("Reject is atomic and returns the untouched annotated fallback") {
    val fixture = parsedFixture("type Aux = String")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val originalBody = existing.impl.body

    val input = fixture.input(Some(existing))
    place(
      input,
      fixture.generatedType,
      MemberConflictPolicy.Reject
    ) match
      case ExpansionOutcome.Rejected(diagnostics) =>
        assertEquals(diagnostics.size, 1)
        assert(
          diagnostics.head.message.contains("generated member `Aux` conflicts"),
          clue(diagnostics.head.message)
        )
      case other => fail(s"expected Rejected, found $other")

    assert(existing.impl.body.zip(originalBody).forall(_ eq _), clue(existing.impl.body))
    assertEquals(directTypesNamed(existing, "Aux").size, 1)
  }

  test("the generic member policy preserves term and type namespace separation") {
    val fixture = parsedFixture("val Aux: Int = 1")
    given Context = fixture.context

    val input = fixture.input(fixture.companion)
    val output = structured(input):
      place(
        input,
        fixture.generatedType,
        MemberConflictPolicy.PreserveExisting
      )

    val merged = output.companion.getOrElse(fail("missing merged companion"))
    assertEquals(directTypesNamed(merged, "Aux"), List(fixture.generatedType))
    assert(
      merged.impl.body.exists {
        case value: ValDef => value.name.toString == "Aux"
        case _ => false
      },
      clue(merged.impl.body)
    )
  }

  test("placement preserves the supplied TypeDef and its rhs opaquely") {
    val fixture = parsedFixture("val preserved: Int = 1")
    given Context = fixture.context

    val input = fixture.input(fixture.companion)
    val output = structured(input):
      place(
        input,
        fixture.generatedType,
        MemberConflictPolicy.PreserveExisting
      )

    val inserted = directTypesNamed(
      output.companion.getOrElse(fail("missing companion")),
      "Aux"
    ).headOption.getOrElse(fail("missing inserted type"))
    assert(inserted.eq(fixture.generatedType), clue(inserted))
    assert(inserted.rhs.eq(fixture.generatedType.rhs), clue(inserted.rhs))
  }

  private final case class Fixture(
      primary: TypeDef,
      companion: Option[ModuleDef],
      generatedType: TypeDef,
      currentAnnotation: Tree,
      context: Context
  ):
    def input(existingCompanion: Option[ModuleDef]): ExpansionInput =
      paradise3.api.ExpansionInputTestFactory(
        "current",
        primary,
        existingCompanion,
        Set("Add", "GeneratedTypeOwner"),
        Some(currentAnnotation)
      )(using context)

  private def shape(code: String): ExpansionTargetView =
    val (stats, context) = parsedStats(code, "AuxPlacementShape.scala")
    given Context = context
    val primary = typeDefNamed(stats, stats.collectFirst {
      case value: TypeDef if value.isClassDef => value.name.toString
    }.getOrElse(fail(s"missing class definition in $stats")))
    primary match
      case value: TypeDef =>
        ExpansionTargetView.decode(value) match
          case Right(view) => view
          case Left(diagnostic) => fail(diagnostic.message)

  private def parsedFixture(existingBody: String = ""): Fixture =
    val companion =
      if existingBody.isEmpty then ""
      else s"""
              |object Add:
              |${indent(existingBody)}
              |""".stripMargin
    val source =
      s"""@current @later
         |trait Add[N <: Nat, M <: Nat]:
         |  type Out <: Nat
         |$companion
         |object GeneratedTypeOwner:
         |  type Aux[N <: Nat, M <: Nat, Out0 <: Nat] =
         |    Add[N, M] { type Out = Out0 }
         |""".stripMargin
    val (stats, context) = parsedStats(source, "AuxPlacementFixture.scala")
    given Context = context
    val primary = typeDefNamed(stats, "Add")
    val existingCompanion = stats.collectFirst {
      case value: ModuleDef if value.name.toString == "Add" => value
    }
    val owner = stats.collectFirst {
      case value: ModuleDef if value.name.toString == "GeneratedTypeOwner" => value
    }.getOrElse(fail(s"missing generated type owner in $stats"))
    val generatedType = owner.impl.body.collectFirst {
      case value: TypeDef if value.name.toString == "Aux" => value
    }.getOrElse(fail(s"missing generated type in ${owner.impl.body}"))
    val currentAnnotation = Trees.mods(primary).annotations.headOption
      .getOrElse(fail("missing current annotation"))
    Fixture(primary, existingCompanion, generatedType, currentAnnotation, context)

  private def parsedStats(code: String, sourceName: String): (List[Tree], Context) =
    val unit = CompilationUnit(sourceName, code)
    val context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val parsed = new Parsers.Parser(unit.source)(using context).parse()
    val stats = parsed match
      case PackageDef(_, values) => values
      case tree => List(tree)
    (stats, context)

  private def directTypesNamed(companion: ModuleDef, name: String)(using Context): List[TypeDef] =
    companion.impl.body.collect {
      case member: TypeDef if member.name.toString == name => member
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
