package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.ContextBase
import dotty.tools.dotc.core.Flags

import scala.meta.*

class InstanceCurriedMethodDefinitionBuilderSuite extends munit.FunSuite:
  test("authors the exact neutral curried-method factory topology") {
    val definition = InstanceCurriedMethodDefinitionBuilder.definition(
      InstanceCurriedMethodSourceShapeDecoder.SourceShape(
        traitName = "Curried",
        enclosingTypeParameterName = "A",
        methodName = "combine",
        firstParameterName = "a",
        secondParameterName = "b",
        carrierName = "combineFunction"
      )
    )

    assertEquals(
      definition.syntax,
      "def instance[A](combineFunction: A => A => A): Curried[A] = new Curried[A] { override def combine(a: A)(b: A): A = combineFunction(a)(b) }"
    )
    definition.paramClauseGroups match
      case List(group) =>
        assertEquals(group.tparamClause.values.map(_.name.value), List("A"))
        assertEquals(
          group.paramClauses.map(_.values.map(_.name.value)),
          List(List("combineFunction"))
        )
      case other => fail(s"expected one factory clause group, found $other")
    definition.body match
      case implementation: Term.NewAnonymous =>
        implementation.templ.stats match
          case List(method: Defn.Def) =>
            assertEquals(
              method.paramClauseGroups.flatMap(_.paramClauses).map(_.values.map(_.name.value)),
              List(List("a"), List("b"))
            )
            method.body match
              case Term.Apply(
                    Term.Apply(Term.Name("combineFunction"), List(Term.Name("a"))),
                    List(Term.Name("b"))
                  ) => ()
              case other => fail(s"expected successive carrier applications, found $other")
          case other => fail(s"expected one anonymous override, found $other")
      case other => fail(s"expected anonymous implementation, found $other")
  }

  test("lowers through C063 to the exact curried raw topology") {
    val base = new ContextBase
    given dotty.tools.dotc.core.Contexts.Context = base.initialCtx
    val lowered = InstanceCurriedMethodDefinitionBuilder
      .lower(
        InstanceCurriedMethodSourceShapeDecoder.SourceShape(
          "Curried",
          "A",
          "combine",
          "a",
          "b",
          "combineFunction"
        )
      )
      .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

    assertEquals(
      lowered.generatedSource,
      "def instance[A](combineFunction: A => A => A): Curried[A] = new Curried[A] { override def combine(a: A)(b: A): A = combineFunction(a)(b) }"
    )
    assertEquals(lowered.virtualSourceName, "AuxifyGeneratedCurriedInstance.scala")
    lowered.tree.paramss match
      case List(List(typeParameter: untpd.TypeDef), List(carrier: untpd.ValDef)) =>
        assertEquals(typeParameter.name.toString, "A")
        assertEquals(carrier.name.toString, "combineFunction")
        carrier.tpt match
          case untpd.Function(
                List(untpd.Ident(first)),
                untpd.Function(List(untpd.Ident(second)), untpd.Ident(result))
              ) =>
            assertEquals(
              List(first.toString, second.toString, result.toString),
              List("A", "A", "A")
            )
          case other => fail(s"expected nested unary carrier type, found $other")
      case other => fail(s"expected one type parameter and one strict carrier, found $other")
    lowered.tree.rhs match
      case untpd.New(template: untpd.Template) =>
        template.body match
          case List(method: untpd.DefDef) =>
            assertEquals(method.name.toString, "combine")
            assertEquals(method.mods.flags, Flags.Override | Flags.Method)
            method.trailingParamss match
              case List(List(first: untpd.ValDef), List(second: untpd.ValDef)) =>
                assertEquals(first.name.toString, "a")
                assertEquals(second.name.toString, "b")
              case other => fail(s"expected two unary clauses, found $other")
            method.rhs match
              case untpd.Apply(
                    untpd.Apply(untpd.Ident(callee), List(untpd.Ident(first))),
                    List(untpd.Ident(second))
                  ) =>
                assertEquals(callee.toString, "combineFunction")
                assertEquals(first.toString, "a")
                assertEquals(second.toString, "b")
              case other => fail(s"expected successive raw applications, found $other")
          case other => fail(s"expected one anonymous override, found $other")
      case other => fail(s"expected anonymous implementation, found $other")
  }
