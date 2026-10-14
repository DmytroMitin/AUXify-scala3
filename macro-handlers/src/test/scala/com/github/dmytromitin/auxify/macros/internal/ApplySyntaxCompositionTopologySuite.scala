package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Names.termName
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{ExpansionInput, ExpansionOutcome}

class ApplySyntaxCompositionTopologySuite extends munit.FunSuite:
  private val applyName = "com.github.dmytromitin.auxify.macros.apply"
  private val syntaxName = "com.github.dmytromitin.auxify.macros.syntax"

  test("both orders keep one primary extension and freshen past tail term names") {
    List(List(applyName, syntaxName), List(syntaxName, applyName)).foreach { order =>
      withExpansionInput(
        """@current
          |trait FreshNames[Element]:
          |  def merge(left: Element, right: Element): Element
          |  def inst(value: Element): Element = value
          |  type Alias = Element
          |  def inst1(value: Element): Element = value
          |""".stripMargin,
        "FreshNames",
        order
      ) { (input, context) =>
        given Context = context
        val output = new SyntaxHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport.materialize(input, changes)
          case other => fail(s"expected structured output for $order, found $other")

        val modules = output.companion.toList.flatMap(_.impl.body).collect {
          case module: ModuleDef if module.name == termName("syntax") => module
        }
        assertEquals(modules.size, 1, clue(order))
        modules.head.impl.body match
          case List(ExtMethods(_, List(method: DefDef))) =>
            assertEquals(method.name.toString, "merge", clue(order))
            assertEquals(
              method.trailingParamss.map(_.map(_.name.toString)),
              List(List("right"), List("inst2")),
              clue(order)
            )
          case other => fail(s"expected one extension and one method for $order, found $other")
      }
    }
  }

  test("a tail alias named inst does not occupy the evidence term namespace") {
    withExpansionInput(
      """@current
        |trait AliasOnly[A]:
        |  def combine(left: A, right: A): A
        |  type inst = A
        |""".stripMargin,
      "AliasOnly",
      List(applyName, syntaxName)
    ) { (input, context) =>
      given Context = context
      val output = new SyntaxHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          StructuredOutcomeTestSupport.materialize(input, changes)
        case other => fail(s"expected alias-only structured output, found $other")

      val module = output.companion.toList.flatMap(_.impl.body).collectFirst {
        case value: ModuleDef if value.name == termName("syntax") => value
      }.getOrElse(fail("missing syntax module"))
      module.impl.body match
        case List(ExtMethods(_, List(method: DefDef))) =>
          assertEquals(
            method.trailingParamss.map(_.map(_.name.toString)),
            List(List("right"), List("inst"))
          )
        case other => fail(s"expected one alias-only extension method, found $other")
    }
  }

  test("both source orders preserve existing companion member order before generated members") {
    List(List(applyName, syntaxName), List(syntaxName, applyName)).foreach { order =>
      withParsedComposition(
        """@current
          |trait OrderedCompanion[A]:
          |  def combine(left: A, right: A): A
          |  def twice(value: A): A = combine(value, value)
          |
          |object OrderedCompanion:
          |  val before = 1
          |  def existing = 2
          |  val after = 3
          |""".stripMargin,
        "OrderedCompanion",
        order
      ) { (primary, companion, currentAnnotation, context) =>
        given Context = context
        val output = order.foldLeft(StructuredExpansionOutput(primary, companion)) {
          case (staged, annotationName) =>
            val input = paradise3.api.ExpansionInputTestFactory(
              annotationName,
              staged.primary,
              staged.companion,
              Set("OrderedCompanion"),
              Some(currentAnnotation),
              order
            )
            val outcome =
              if annotationName == applyName then new ApplyHandler().expand(input)
              else new SyntaxHandler().expand(input)
            outcome match
              case ExpansionOutcome.Structured(changes) =>
                StructuredOutcomeTestSupport.materialize(input, changes)
              case other => fail(s"expected structured output for $order, found $other")
        }

        val names = output.companion.getOrElse(fail("missing ordered companion")).impl.body.collect {
          case member: MemberDef => member.name.toString
        }
        assertEquals(
          names,
          List("before", "existing", "after") ++
            (if order.head == applyName then List("apply", "syntax") else List("syntax", "apply")),
          clue(order)
        )
      }
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String,
      order: List[String]
  )(run: (ExpansionInput, Context) => A): A =
    val unit = CompilationUnit(s"${className}ApplySyntaxTopology.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val currentAnnotation = Trees.mods(primary).annotations.head
    val input = paradise3.api.ExpansionInputTestFactory(
      syntaxName,
      primary,
      None,
      Set(className),
      Some(currentAnnotation),
      order
    )
    run(input, summon[Context])

  private def withParsedComposition[A](
      source: String,
      className: String,
      order: List[String]
  )(run: (TypeDef, Option[ModuleDef], Tree, Context) => A): A =
    val unit = CompilationUnit(s"${className}ApplySyntaxTopology.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst {
      case value: ModuleDef if value.name.toString == className => value
    }
    val currentAnnotation = Trees.mods(primary).annotations.head
    run(primary, companion, currentAnnotation, summon[Context])
