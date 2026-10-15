package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome, ExpansionTargetBodyView}

class ApplyInstanceCurriedCompositionAdmissionSuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val instanceName = "com.github.dmytromitin.auxify.macros.instance"
  private val syntaxName = "com.github.dmytromitin.auxify.macros.syntax"

  private val admittedOrders = List(
    List(applyName, instanceName),
    List(instanceName, applyName)
  )

  test("both exact orders preserve participant and curried-tail body evidence") {
    admittedOrders.foreach { order =>
      withExpansionInput(richSource("ObservedCurried"), "ObservedCurried", order) {
        (input, context) =>
          given Context = context
          assertEquals(input.sourceOrderedHandledAnnotationNames, order)
          assertEquals(
            input.targetBodyView.toOption.toList.flatMap(_.members.map(_.kind)),
            List(
              ExpansionTargetBodyView.DirectMemberKind.Method,
              ExpansionTargetBodyView.DirectMemberKind.Type,
              ExpansionTargetBodyView.DirectMemberKind.Method,
              ExpansionTargetBodyView.DirectMemberKind.Type,
              ExpansionTargetBodyView.DirectMemberKind.Method
            ),
            clue(order)
          )
          assertEquals(
            input.targetTypeStructureView.toOption.toList.flatMap(
              _.directTypeMembers.map(member => member.name -> member.bodyIndex)
            ),
            List("Item" -> 1, "Value" -> 3),
            clue(order)
          )
          new InstanceHandler().expand(input) match
            case ExpansionOutcome.Structured(_) => ()
            case other => fail(s"expected admitted exact cohort for $order, found $other")
      }
    }
  }

  test("standalone instance remains admitted for the curried family") {
    withExpansionInput(richSource("StandaloneCurried"), "StandaloneCurried", List(instanceName)) {
      (input, context) =>
        given Context = context
        new InstanceHandler().expand(input) match
          case ExpansionOutcome.Structured(_) => ()
          case other => fail(s"expected admitted standalone instance, found $other")
    }
  }

  test("the curried composition rejects a third handled participant") {
    List(
      List(applyName, instanceName, syntaxName),
      List(instanceName, applyName, syntaxName)
    ).foreach { order =>
      withExpansionInput(richSource("ThreeCurriedParticipants"), "ThreeCurriedParticipants", order) {
        (input, context) =>
          given Context = context
          new InstanceHandler().expand(input) match
            case ExpansionOutcome.Rejected(diagnostics) =>
              assertEquals(
                diagnostics.map(_.message),
                List(
                  "unsupported @instance composition shape for `ThreeCurriedParticipants`: curried-method composition requires exactly the @apply and @instance participant cohort"
                ),
                clue(order)
              )
            case other => fail(s"expected exact-cohort rejection for $order, found $other")
      }
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String,
      order: List[String]
  )(run: (ExpansionInput, Context) => A): A =
    val unit = CompilationUnit(s"${className}Composition.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val primary = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, List(value: TypeDef)) => value
      case value: TypeDef => value
      case other => fail(s"missing primary TypeDef in $other")
    val input = paradise3.api.ExpansionInputTestFactory(
      instanceName,
      primary,
      None,
      Set(className),
      Some(Trees.mods(primary).annotations.head),
      order
    )
    run(input, summon[Context])

  private def richSource(className: String): String =
    s"""@current
       |trait $className[A]:
       |  def combine(a: A)(b: A): A
       |  type Item = A
       |  def twice(a: A): A = combine(a)(a)
       |  type Value = A
       |  def fold3(a: A, b: A, c: A): A = combine(combine(a)(b))(c)
       |""".stripMargin
