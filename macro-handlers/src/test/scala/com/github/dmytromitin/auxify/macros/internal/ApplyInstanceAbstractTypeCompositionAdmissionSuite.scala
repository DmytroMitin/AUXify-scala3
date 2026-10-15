package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome, ExpansionTargetBodyView}

class ApplyInstanceAbstractTypeCompositionAdmissionSuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val instanceName = "com.github.dmytromitin.auxify.macros.instance"
  private val syntaxName = "com.github.dmytromitin.auxify.macros.syntax"

  private val admittedOrders = List(
    List(applyName, instanceName),
    List(instanceName, applyName)
  )

  test("both exact orders preserve participant, body, and type-structure evidence") {
    admittedOrders.foreach { order =>
      withExpansionInput(richSource("ObservedShape"), "ObservedShape", order) {
        (input, context) =>
          given Context = context
          assertEquals(input.sourceOrderedHandledAnnotationNames, order)
          assertEquals(
            input.targetBodyView.toOption.toList.flatMap(_.members.map(_.kind)),
            List(
              ExpansionTargetBodyView.DirectMemberKind.Type,
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
            List("Out" -> 0, "Item" -> 1, "Value" -> 3),
            clue(order)
          )
          new InstanceHandler().expand(input) match
            case ExpansionOutcome.Structured(_) => ()
            case other => fail(s"expected admitted exact cohort for $order, found $other")
      }
    }
  }

  test("the exact cohort also admits the no-tail baseline in both orders") {
    admittedOrders.foreach { order =>
      withExpansionInput(
        """@current
          |trait NoTail[A]:
          |  type Out
          |""".stripMargin,
        "NoTail",
        order
      ) { (input, context) =>
        given Context = context
        new InstanceHandler().expand(input) match
          case ExpansionOutcome.Structured(_) => ()
          case other => fail(s"expected admitted no-tail cohort for $order, found $other")
      }
    }
  }

  test("the abstract-type composition rejects a third handled participant") {
    List(
      List(applyName, instanceName, syntaxName),
      List(instanceName, applyName, syntaxName)
    ).foreach { order =>
      withExpansionInput(richSource("ThreeParticipants"), "ThreeParticipants", order) {
        (input, context) =>
          given Context = context
          new InstanceHandler().expand(input) match
            case ExpansionOutcome.Rejected(diagnostics) =>
              assertEquals(
                diagnostics.map(_.message),
                List(
                  "unsupported @instance composition shape for `ThreeParticipants`: abstract-type-member composition requires exactly the @apply and @instance participant cohort"
                ),
                clue(order)
              )
            case other => fail(s"expected exact-cohort rejection for $order, found $other")
      }
    }
  }

  test("the new gate does not change the historical method-family routing") {
    withExpansionInput(
      """@current
        |trait Historical[A]:
        |  def empty: A
        |  def combine(left: A, right: A): A
        |""".stripMargin,
      "Historical",
      List(applyName, instanceName, syntaxName)
    ) { (input, context) =>
      given Context = context
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(_) => ()
        case other => fail(s"expected unchanged historical routing, found $other")
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
       |  type Out
       |  type Item = A
       |  def identity(value: A): A = value
       |  type Value = A
       |  def pick(first: A, second: A): A = first
       |""".stripMargin
