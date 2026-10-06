package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}

import scala.meta.*

class InstanceAbstractTypeMemberDefinitionBuilderSuite extends munit.FunSuite:
  private val shape =
    InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape(
      traitName = "HasOut",
      enclosingTypeParameterName = "A",
      memberName = "Out",
      generatedTypeParameterName = "Out0"
    )

  test("authors the exact C060 neutral factory topology") {
    val definition = InstanceAbstractTypeMemberDefinitionBuilder.definition(shape)

    assertEquals(
      definition.syntax,
      """def instance[A, Out0]: HasOut[A] {
        |  type Out = Out0
        |} = new HasOut[A] { type Out = Out0 }""".stripMargin
    )
    definition.paramClauseGroups match
      case List(group) =>
        assertEquals(group.tparamClause.values.map(_.name.value), List("A", "Out0"))
        assertEquals(
          group.paramClauses,
          Nil,
          clues(definition.structure)
        )
      case other => fail(s"expected one clause group, found $other")
    assertEquals(
      definition.body.asInstanceOf[Term.NewAnonymous].templ.stats.map(_.syntax),
      List("type Out = Out0")
    )
  }

  test("lowers through C060 to the exact raw refined factory topology") {
    withContext {
      val lowered = InstanceAbstractTypeMemberDefinitionBuilder
        .lower(shape)
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

      assertEquals(
        lowered.generatedSource,
        "def instance[A, Out0]: HasOut[A] { type Out = Out0 } = new HasOut[A] { type Out = Out0 }"
      )
      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedHasOutInstance.scala")
      val method = lowered.tree
      assertEquals(method.name.toString, "instance")
      method.paramss match
        case List(List(first: untpd.TypeDef, second: untpd.TypeDef)) =>
          assertEquals(first.name.toString, "A")
          assertEquals(second.name.toString, "Out0")
        case other => fail(s"expected exactly two method type parameters, found $other")
      method.tpt match
        case refinement: untpd.RefinedTypeTree =>
          assertApplied(refinement.tpt, "HasOut", "A")
          refinement.refinements match
            case List(alias: untpd.TypeDef) =>
              assertAlias(alias, "Out", "Out0")
            case other => fail(s"expected exactly one result refinement, found $other")
        case other => fail(s"expected refined result type, found $other")
      method.rhs match
        case untpd.New(template: untpd.Template) =>
          template.parentsOrDerived match
            case parent :: Nil => assertApplied(parent, "HasOut", "A")
            case other => fail(s"expected exactly one anonymous parent, found $other")
          template.body match
            case List(alias: untpd.TypeDef) =>
              assertAlias(alias, "Out", "Out0")
            case other => fail(s"expected exactly one concrete anonymous alias, found $other")
        case other => fail(s"expected anonymous implementation, found $other")
    }
  }

  test("lowers coherently renamed and collision-freshened roles") {
    withContext {
      val renamed = shape.copy(
        traitName = "Container",
        enclosingTypeParameterName = "Element0",
        memberName = "Element",
        generatedTypeParameterName = "Element1"
      )
      val method = InstanceAbstractTypeMemberDefinitionBuilder
        .lower(renamed)
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), _.tree)

      assertEquals(
        method.leadingTypeParams.map(_.name.toString),
        List("Element0", "Element1")
      )
      method.tpt match
        case refinement: untpd.RefinedTypeTree =>
          assertApplied(refinement.tpt, "Container", "Element0")
          assertAlias(
            refinement.refinements.head.asInstanceOf[untpd.TypeDef],
            "Element",
            "Element1"
          )
        case other => fail(s"expected renamed refined result type, found $other")
    }
  }

  private def assertAlias(
      alias: untpd.TypeDef,
      memberName: String,
      targetName: String
  ): Unit =
    assertEquals(alias.name.toString, memberName)
    alias.rhs match
      case untpd.Ident(name) => assertEquals(name.toString, targetName)
      case other => fail(s"expected concrete alias target $targetName, found $other")

  private def assertApplied(
      tree: untpd.Tree,
      targetName: String,
      argumentName: String
  ): Unit =
    tree match
      case untpd.AppliedTypeTree(
            untpd.Ident(target),
            List(untpd.Ident(argument))
          ) =>
        assertEquals(target.toString, targetName)
        assertEquals(argument.toString, argumentName)
      case other => fail(s"expected $targetName[$argumentName], found $other")

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
