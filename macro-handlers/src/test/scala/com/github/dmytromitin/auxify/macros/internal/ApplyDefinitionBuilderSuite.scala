package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Flags

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class ApplyDefinitionBuilderSuite extends munit.FunSuite:
  private val VirtualSourceName = "AuxifyGeneratedShowApply.scala"

  test("definition constructs the canonical neutral apply shape") {
    val definition: Defn.Def = ApplyDefinitionBuilder.definition("Show", "A")

    assertEquals(definition.mods, Nil)
    assertEquals(definition.name.value, "apply")
    assertEquals(definition.paramClauseGroups.size, 1)

    val group = definition.paramClauseGroups.head
    assertEquals(group.tparamClause.values.size, 1)
    val typeParameter = group.tparamClause.values.head
    assertEquals(typeParameter.name.value, "A")
    assertEquals(typeParameter.mods, Nil)
    assertEquals(typeParameter.tparamClause.values, Nil)
    assertEquals(typeParameter.bounds.lo, None)
    assertEquals(typeParameter.bounds.hi, None)
    assertEquals(typeParameter.bounds.context, Nil)
    assertEquals(typeParameter.bounds.view, Nil)

    assertEquals(group.paramClauses.size, 1)
    val contextualClause = group.paramClauses.head
    assert(contextualClause.mod.exists(_.isInstanceOf[Mod.Using]))
    assertEquals(contextualClause.values.size, 1)
    val contextualParameter = contextualClause.values.head
    assertEquals(contextualParameter.name.value, "inst")
    assertEquals(contextualParameter.mods.size, 1)
    assert(contextualParameter.mods.head.isInstanceOf[Mod.Using])
    assertEquals(contextualParameter.default, None)
    assertScalametaApplied(contextualParameter.decltpe, "Show", "A")

    assertScalametaApplied(definition.decltpe, "Show", "A")
    definition.body match
      case Term.Name(value) => assertEquals(value, "inst")
      case other => fail(s"expected body Term.Name(inst), found $other")
  }

  test("definition derives the trait and type-parameter names from its inputs") {
    val definition: Defn.Def = ApplyDefinitionBuilder.definition("Evidence", "X")
    val group = definition.paramClauseGroups.head

    assertEquals(group.tparamClause.values.map(_.name.value), List("X"))
    assertScalametaApplied(
      group.paramClauses.head.values.head.decltpe,
      "Evidence",
      "X"
    )
    assertScalametaApplied(definition.decltpe, "Evidence", "X")
  }

  test("Scala 3 quasiquote accepts the minimal dynamic definition splices") {
    val typeParameter = Type.Param(
      Nil,
      Type.Name("X"),
      Type.ParamClause(Nil),
      Type.Bounds(None, None, Nil, Nil)
    )
    val typeParameters = List(typeParameter)
    val target: Type =
      Type.Apply(Type.Name("Evidence"), Type.ArgClause(List(Type.Name("X"))))

    val definition =
      q"def apply[..$typeParameters](using inst: $target): $target = inst"
        .asInstanceOf[Defn.Def]

    assertEquals(definition.paramClauseGroups.size, 1)
    val group = definition.paramClauseGroups.head
    assertEquals(group.tparamClause.values.map(_.name.value), List("X"))
    assertScalametaApplied(
      group.paramClauses.head.values.head.decltpe,
      "Evidence",
      "X"
    )
    assertScalametaApplied(definition.decltpe, "Evidence", "X")
  }

  test("lower returns the peer's exact positioned contextual DefDef") {
    withContext {
      val lowered: ContextualMethodPeerBridge.Lowered =
        ApplyDefinitionBuilder
          .lower("Show", "A")
          .fold(failure => fail(s"${failure.code}: ${failure.detail}"), identity)
      val method: untpd.DefDef = lowered.tree

      assertEquals(
        lowered.generatedSource,
        "def apply[A](using inst: Show[A]): Show[A] = inst"
      )
      assertEquals(lowered.virtualSourceName, VirtualSourceName)
      assertEquals(method.name.toString, "apply")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(method.trailingParamss.size, 1)
      val contextualClause = method.trailingParamss.head
      assertEquals(contextualClause.size, 1)
      val contextual = contextualClause.head match
        case value: untpd.ValDef => value
        case other => fail(s"expected contextual parameter ValDef, found $other")
      assertEquals(contextual.name.toString, "inst")
      assertEquals(contextual.mods.flags, Flags.Param | Flags.Given)
      assertUntypedApplied(contextual.tpt, "Show", "A")
      assertUntypedApplied(method.tpt, "Show", "A")
      method.rhs match
        case untpd.Ident(name) => assertEquals(name.toString, "inst")
        case other => fail(s"expected body Ident(inst), found $other")
      assertAllNonEmptyTreesHaveProvenance(method)
    }
  }

  test("lower reports malformed names through the controlled peer bridge") {
    withContext {
      val result = ApplyDefinitionBuilder.lower("bad-name", "A")
      val failure = result.left.toOption.getOrElse(fail("expected bridge rejection"))

      assertEquals(failure.code, "NEUTRAL_PROJECTION_FAILED")
      assert(failure.detail.nonEmpty)
    }
  }

  private def assertScalametaApplied(
      tree: Option[Type],
      constructor: String,
      argument: String
  ): Unit =
    tree match
      case Some(applied: Type.Apply) =>
        applied.tpe match
          case name: Type.Name => assertEquals(name.value, constructor)
          case other => fail(s"expected constructor $constructor, found $other")
        applied.argClause.values match
          case List(name: Type.Name) => assertEquals(name.value, argument)
          case other => fail(s"expected argument $argument, found $other")
      case other => fail(s"expected $constructor[$argument], found $other")

  private def assertUntypedApplied(
      tree: untpd.Tree,
      constructor: String,
      argument: String
  ): Unit =
    tree match
      case untpd.AppliedTypeTree(
            untpd.Ident(actualConstructor),
            List(untpd.Ident(actualArgument))
          ) =>
        assertEquals(actualConstructor.toString, constructor)
        assertEquals(actualArgument.toString, argument)
      case other => fail(s"expected AppliedTypeTree($constructor, $argument), found $other")

  private def assertAllNonEmptyTreesHaveProvenance(
      tree: untpd.Tree
  )(using Context): Unit =
    val traverser = new untpd.UntypedTreeTraverser:
      override def traverse(current: untpd.Tree)(using Context): Unit =
        if !current.isEmpty then
          assert(current.source.exists, clues(current))
          assertEquals(current.source.path, VirtualSourceName, clues(current))
          assert(current.span.exists, clues(current))
          current match
            case definition: untpd.DefTree =>
              definition.mods.annotations.foreach(annotation => traverse(annotation))
            case _ => ()
          traverseChildren(current)
    traverser.traverse(tree)

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
