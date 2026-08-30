package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Flags
import dotty.tools.dotc.core.Symbols.NoSymbol

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class ApplyAddOutBridgeConsumerSuite extends munit.FunSuite:
  test("input 037 lowers the canonical bounded Add.Out method with exact topology and provenance") {
    withContext {
      assertLowered(
        ApplyDefinitionBuilder.FullShape(
          typeClassName = "Add",
          firstTypeParameterName = "N",
          secondTypeParameterName = "M",
          upperBoundTypeName = "Nat",
          resultTypeMemberName = "Out"
        ),
        "def apply[N <: Nat, M <: Nat](using inst: Add[N, M]): Add[N, M] { type Out = inst.Out } = inst",
        "N",
        "M",
        "Nat",
        "Add",
        "Out"
      )
    }
  }

  test("input 037 lowers independently renamed legal binders and members") {
    withContext {
      assertLowered(
        ApplyDefinitionBuilder.FullShape(
          typeClassName = "Combine",
          firstTypeParameterName = "Left",
          secondTypeParameterName = "Right",
          upperBoundTypeName = "Natural",
          resultTypeMemberName = "Result"
        ),
        "def apply[Left <: Natural, Right <: Natural](using inst: Combine[Left, Right]): Combine[Left, Right] { type Result = inst.Result } = inst",
        "Left",
        "Right",
        "Natural",
        "Combine",
        "Result"
      )
    }
  }

  test("input 037 rejects mismatched upper bounds without a legacy fallback") {
    withContext {
      val malformed =
        Scala3(
          "def apply[N <: Nat, M <: Other](using inst: Add[N, M]): Add[N, M] { type Out = inst.Out } = inst"
        ).parse[Stat].get.asInstanceOf[Defn.Def]

      val failure = ContextualMethodPeerBridge
        .lower(malformed, "AuxifyMalformedAddApply.scala")
        .left
        .toOption
        .getOrElse(fail("mismatched upper bounds unexpectedly lowered"))

      assertEquals(failure.code, "NEUTRAL_PROJECTION_FAILED")
      assert(
        failure.detail.startsWith(
          "NEUTRAL_SCOPED037_TYPE_PARAMETER_UPPER_BOUND_MISMATCH:"
        ),
        clues(failure)
      )
    }
  }

  private def assertLowered(
      shape: ApplyDefinitionBuilder.FullShape,
      expectedGeneratedSource: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundName: String,
      constructorName: String,
      memberName: String
  )(using Context): Unit =
    val definition: Defn.Def = ApplyDefinitionBuilder.fullDefinition(shape)
    val lowered: ContextualMethodPeerBridge.Lowered =
      ApplyDefinitionBuilder
        .lowerFull(shape)
        .fold(failure => fail(s"${failure.code}: ${failure.detail}"), identity)
    val method: untpd.DefDef = lowered.tree
    val virtualSourceName = s"AuxifyGenerated${shape.typeClassName}Apply.scala"

    assertEquals(lowered.generatedSource, expectedGeneratedSource)
    assertEquals(definition.name.value, "apply")
    assertEquals(lowered.virtualSourceName, virtualSourceName)
    assertEquals(method.source.path, virtualSourceName)
    assertEquals(method.name.toString, "apply")
    assertEquals(method.mods.flags, Flags.Method)
    assertEquals(
      method.leadingTypeParams.map(_.name.toString),
      List(firstTypeParameterName, secondTypeParameterName)
    )
    method.leadingTypeParams.foreach { parameter =>
      assertEquals(parameter.mods.flags, Flags.Param)
      parameter.rhs match
        case untpd.TypeBoundsTree(lo, untpd.Ident(hi), alias) =>
          assert(lo.isEmpty)
          assertEquals(hi.toString, upperBoundName)
          assert(alias.isEmpty)
        case other => fail(s"expected upper-only TypeBoundsTree, found $other")
    }

    val contextual = method.trailingParamss match
      case List(List(value: untpd.ValDef)) => value
      case other => fail(s"expected one contextual parameter, found $other")
    assertEquals(contextual.name.toString, "inst")
    assertEquals(contextual.mods.flags, Flags.Param | Flags.Given)
    assertApplied(
      contextual.tpt,
      constructorName,
      firstTypeParameterName,
      secondTypeParameterName
    )

    method.tpt match
      case untpd.RefinedTypeTree(base, List(member: untpd.TypeDef)) =>
        assertApplied(
          base,
          constructorName,
          firstTypeParameterName,
          secondTypeParameterName
        )
        assertEquals(member.name.toString, memberName)
        member.rhs match
          case untpd.Select(untpd.Ident(prefix), selected) =>
            assertEquals(prefix.toString, "inst")
            assertEquals(selected.toString, memberName)
          case other => fail(s"expected selected type alias, found $other")
      case other => fail(s"expected one-member RefinedTypeTree, found $other")

    method.rhs match
      case untpd.Ident(name) => assertEquals(name.toString, "inst")
      case other => fail(s"expected body Ident(inst), found $other")

    assertProvenance(method, virtualSourceName, lowered.generatedSource.length)

  private def assertApplied(
      tree: untpd.Tree,
      constructorName: String,
      firstArgumentName: String,
      secondArgumentName: String
  ): Unit =
    tree match
      case untpd.AppliedTypeTree(
            untpd.Ident(constructor),
            List(untpd.Ident(first), untpd.Ident(second))
          ) =>
        assertEquals(constructor.toString, constructorName)
        assertEquals(first.toString, firstArgumentName)
        assertEquals(second.toString, secondArgumentName)
      case other =>
        fail(
          s"expected $constructorName[$firstArgumentName, $secondArgumentName], found $other"
        )

  private def assertProvenance(
      tree: untpd.Tree,
      virtualSourceName: String,
      generatedSourceLength: Int
  )(using Context): Unit =
    val traverser = new untpd.UntypedTreeTraverser:
      override def traverse(current: untpd.Tree)(using Context): Unit =
        if !current.isEmpty then
          assert(current.source.exists, clues(current))
          assertEquals(current.source.path, virtualSourceName, clues(current))
          assert(current.span.exists, clues(current))
          assert(current.span.start >= 0, clues(current))
          assert(current.span.start <= current.span.point, clues(current))
          assert(current.span.point <= current.span.end, clues(current))
          assert(current.span.end <= generatedSourceLength, clues(current))
          assertEquals(current.symbol, NoSymbol, clues(current))
          assert(!current.isInstanceOf[untpd.TypedSplice], clues(current))
          current match
            case definition: untpd.DefTree =>
              definition.mods.annotations.foreach(annotation => traverse(annotation))
            case _ => ()
          traverseChildren(current)
    traverser.traverse(tree)

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
