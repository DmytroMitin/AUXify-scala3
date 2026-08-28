package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers
import paradise3.api.{AnnotatedClassView, ExpansionInput, ExpansionOutcome, ExpansionTargetProfile, StructuredExpansionOutput}
import paradise3.api.helpers.{CompanionTypeConflictPolicy, ExpansionHelpers}

/** Test-only consumption of Macro-Paradise input 040.
  *
  * The raw `Aux` TypeDef below is deliberately only a placement fixture.
  * Quasiquotes input 039 remains the production owner of exact neutral-alias
  * validation and lowering.
  */
class AuxPlacementConsumerSuite extends munit.FunSuite:
  test("input 040 exposes the profile and normalized canonical two-upper-bounded trait shape") {
    assertEquals(
      ExpansionTargetProfile.TwoUpperBoundedGenericTrait.toString,
      "TwoUpperBoundedGenericTrait"
    )

    val canonical = shape(
      """trait Add[N <: Nat, M <: Nat]:
        |  type Out <: Nat
        |""".stripMargin
    )

    assertEquals(canonical.definitionKind, AnnotatedClassView.DefinitionKind.Trait)
    assertEquals(canonical.typeParameters.map(_.name), List("N", "M"))
    assert(canonical.typeParameters.forall(_.variance == AnnotatedClassView.Variance.Invariant))
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

    val output = structured:
      ExpansionHelpers.addTypeToCompanion(
        fixture.input(None),
        fixture.generatedType,
        CompanionTypeConflictPolicy.PreserveExisting
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

    val output = structured:
      ExpansionHelpers.addTypeToCompanion(
        fixture.input(Some(existing)),
        fixture.generatedType,
        CompanionTypeConflictPolicy.PreserveExisting
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

    val output = structured:
      ExpansionHelpers.addTypeToCompanion(
        fixture.input(Some(existing)),
        fixture.generatedType,
        CompanionTypeConflictPolicy.PreserveExisting
      )

    assert(output.companion.getOrElse(fail("missing companion")).eq(existing), clue(output.companion))
    assertEquals(directTypesNamed(existing, "Aux").size, 1)
  }

  test("Reject is atomic and returns the untouched annotated fallback") {
    val fixture = parsedFixture("type Aux = String")
    given Context = fixture.context
    val existing = fixture.companion.getOrElse(fail("missing existing companion"))
    val originalBody = existing.impl.body

    ExpansionHelpers.addTypeToCompanion(
      fixture.input(Some(existing)),
      fixture.generatedType,
      CompanionTypeConflictPolicy.Reject
    ) match
      case ExpansionOutcome.Rejected(diagnostics, fallback) =>
        assert(fallback.eq(fixture.primary), clue(fallback))
        assertEquals(diagnostics.size, 1)
        assert(
          diagnostics.head.message.contains("generated companion type `Aux` conflicts"),
          clue(diagnostics.head.message)
        )
      case other => fail(s"expected Rejected, found $other")

    assert(existing.impl.body.zip(originalBody).forall(_ eq _), clue(existing.impl.body))
    assertEquals(directTypesNamed(existing, "Aux").size, 1)
  }

  test("same-spelling direct term does not occupy the type namespace") {
    val fixture = parsedFixture("val Aux: Int = 1")
    given Context = fixture.context

    val output = structured:
      ExpansionHelpers.addTypeToCompanion(
        fixture.input(fixture.companion),
        fixture.generatedType,
        CompanionTypeConflictPolicy.PreserveExisting
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

    val output = structured:
      ExpansionHelpers.addTypeToCompanion(
        fixture.input(fixture.companion),
        fixture.generatedType,
        CompanionTypeConflictPolicy.PreserveExisting
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
      ExpansionInput(
        "current",
        primary,
        existingCompanion,
        Set("Add", "GeneratedTypeOwner"),
        Some(currentAnnotation)
      )

  private def shape(code: String): AnnotatedClassView =
    val (stats, context) = parsedStats(code, "AuxPlacementShape.scala")
    given Context = context
    val primary = typeDefNamed(stats, stats.collectFirst {
      case value: TypeDef if value.isClassDef => value.name.toString
    }.getOrElse(fail(s"missing class definition in $stats")))
    primary match
      case value: TypeDef =>
        AnnotatedClassView.decode(value) match
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

  private def structured(outcome: ExpansionOutcome): StructuredExpansionOutput =
    outcome match
      case ExpansionOutcome.Structured(output) => output
      case other => fail(s"expected Structured, found $other")
