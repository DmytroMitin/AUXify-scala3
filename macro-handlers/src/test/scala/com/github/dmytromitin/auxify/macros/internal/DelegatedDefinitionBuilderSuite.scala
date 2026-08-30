package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Flags
import dotty.tools.dotc.core.Symbols.NoSymbol

import scala.meta.*

class DelegatedDefinitionBuilderSuite extends munit.FunSuite:
  test("authors and lowers the canonical forwarding method with complete provenance") {
    withContext {
      val shape = DelegatedSourceShapeDecoder.SourceShape(
        "Show",
        "A",
        "show",
        "a",
        "String"
      )

      assertEquals(
        DelegatedDefinitionBuilder.definition(shape).syntax,
        "def show[A](a: A)(using inst: Show[A]): String = inst.show(a)"
      )

      val lowered = DelegatedDefinitionBuilder
        .lower(shape)
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)
      assertEquals(
        lowered.generatedSource,
        "def show[A](a: A)(using inst: Show[A]): String = inst.show(a)"
      )
      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedShowDelegated.scala")
      assertExactShape(lowered.tree, "show", "A", "a", "inst", "Show", "String")
      val trees = allTrees(lowered.tree)
      assertEquals(trees.size, 14)
      trees.foreach { tree =>
        assert(tree.source.exists, clues(tree))
        assertEquals(tree.source.path, "AuxifyGeneratedShowDelegated.scala", clues(tree))
        assertEquals(tree.source.content.mkString, lowered.generatedSource, clues(tree))
        assert(tree.span.exists, clues(tree))
        assert(tree.span.start >= 0, clues(tree))
        assert(tree.span.start <= tree.span.point, clues(tree))
        assert(tree.span.point <= tree.span.end, clues(tree))
        assert(tree.span.end <= lowered.generatedSource.length, clues(tree))
        assertEquals(tree.symbol, NoSymbol, clues(tree))
        assert(!tree.isInstanceOf[untpd.TypedSplice], clues(tree))
      }
    }
  }

  test("derives renamed forwarding topology instead of hard-coding Show or String") {
    withContext {
      val shape = DelegatedSourceShapeDecoder.SourceShape(
        "Render",
        "Element",
        "render",
        "value",
        "Text"
      )
      val lowered = DelegatedDefinitionBuilder
        .lower(shape)
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

      assertEquals(
        lowered.generatedSource,
        "def render[Element](value: Element)(using inst: Render[Element]): Text = inst.render(value)"
      )
      assertExactShape(
        lowered.tree,
        "render",
        "Element",
        "value",
        "inst",
        "Render",
        "Text"
      )
    }
  }

  test("chooses inst1 when the ordinary source parameter occupies inst") {
    withContext {
      val shape = DelegatedSourceShapeDecoder.SourceShape(
        "Display",
        "Value",
        "display",
        "inst",
        "Text"
      )
      val lowered = DelegatedDefinitionBuilder
        .lower(shape)
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

      assertEquals(
        lowered.generatedSource,
        "def display[Value](inst: Value)(using inst1: Display[Value]): Text = inst1.display(inst)"
      )
      assertExactShape(
        lowered.tree,
        "display",
        "Value",
        "inst",
        "inst1",
        "Display",
        "Text"
      )
    }
  }

  private def assertExactShape(
      method: untpd.DefDef,
      methodName: String,
      typeParameterName: String,
      ordinaryName: String,
      contextualName: String,
      constructorName: String,
      resultTypeName: String
  )(using Context): Unit =
    assertEquals(method.name.toString, methodName)
    assertEquals(method.mods.flags, Flags.Method)
    method.leadingTypeParams match
      case List(parameter: untpd.TypeDef) =>
        assertEquals(parameter.name.toString, typeParameterName)
        assertEquals(parameter.mods.flags, Flags.Param)
      case other => fail(s"expected one type parameter, found $other")
    method.trailingParamss match
      case List(List(ordinary: untpd.ValDef), List(contextual: untpd.ValDef)) =>
        assertEquals(ordinary.name.toString, ordinaryName)
        assertEquals(ordinary.mods.flags, Flags.Param)
        assertEquals(contextual.name.toString, contextualName)
        assertEquals(contextual.mods.flags, Flags.Param | Flags.Given)
        ordinary.tpt match
          case untpd.Ident(name) => assertEquals(name.toString, typeParameterName)
          case other => fail(s"expected ordinary parameter type identifier, found $other")
        contextual.tpt match
          case untpd.AppliedTypeTree(untpd.Ident(constructor), List(untpd.Ident(argument))) =>
            assertEquals(constructor.toString, constructorName)
            assertEquals(argument.toString, typeParameterName)
          case other => fail(s"expected unary contextual type, found $other")
      case other => fail(s"expected ordinary and contextual clauses, found $other")
    method.tpt match
      case untpd.Ident(name) => assertEquals(name.toString, resultTypeName)
      case other => fail(s"expected named result type, found $other")
    method.rhs match
      case untpd.Apply(
            untpd.Select(untpd.Ident(receiver), selected),
            List(untpd.Ident(argument))
          ) =>
        assertEquals(receiver.toString, contextualName)
        assertEquals(selected.toString, methodName)
        assertEquals(argument.toString, ordinaryName)
      case other => fail(s"expected selected forwarding application, found $other")

  private def allTrees(tree: untpd.Tree)(using Context): Vector[untpd.Tree] =
    if tree.isEmpty then Vector.empty
    else tree +: directChildren(tree).flatMap(allTrees)

  private def directChildren(tree: untpd.Tree)(using Context): Vector[untpd.Tree] =
    tree match
      case value: untpd.DefDef =>
        value.paramss.flatten.toVector ++ Vector(value.tpt, value.rhs)
      case value: untpd.TypeDef => Vector(value.rhs)
      case value: untpd.ValDef => Vector(value.tpt, value.rhs).filterNot(_.isEmpty)
      case value: untpd.TypeBoundsTree =>
        Vector(value.lo, value.hi, value.alias).filterNot(_.isEmpty)
      case value: untpd.AppliedTypeTree => value.tpt +: value.args.toVector
      case value: untpd.Apply => value.fun +: value.args.toVector
      case value: untpd.Select => Vector(value.qualifier)
      case _ => Vector.empty

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
