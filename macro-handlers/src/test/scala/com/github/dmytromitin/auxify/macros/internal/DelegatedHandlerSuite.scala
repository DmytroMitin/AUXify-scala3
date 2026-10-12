package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome
}

import quasiquotes.definitions.dotty.DelegatedForwardingMethodPeerBridge

class DelegatedHandlerSuite extends munit.FunSuite:
  test("implements the current protocol with the public delegated annotation") {
    val handler: ExpansionHandler = new DelegatedHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.delegated"
    )
  }

  test("derives and places the canonical forwarding method") {
    withExpansionInput(
      """@current
        |trait Show[A]:
        |  def show(a: A): String
        |""".stripMargin,
      "Show"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedMethod(output, "show")
        case other => fail(s"expected structured delegated expansion, found $other")

      assertEquals(method.name.toString, "show")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(method.trailingParamss.map(_.map(_.name.toString)), List(List("a"), List("inst")))
    }
  }

  test("freshens unary evidence across inherited concrete method term roles") {
    withExpansionInput(
      """@current
        |trait FreshShow[A]:
        |  def show(value: A): String
        |  def inst(inst1: A): A = inst1
        |""".stripMargin,
      "FreshShow"
    ) { (input, _, _, context) =>
      given Context = context
      val output = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          StructuredOutcomeTestSupport.materialize(input, changes)
        case other => fail(s"expected structured delegated expansion, found $other")

      val generated = output.companion.toList.flatMap(_.impl.body).collect {
        case method: DefDef if method.name.toString == "show" => method
      }
      assertEquals(generated.size, 1)
      assertEquals(
        generated.head.trailingParamss.map(_.map(_.name.toString)),
        List(List("value"), List("inst2"))
      )
    }
  }

  test("uses normalized alias evidence without reserving its type-only name") {
    withExpansionInput(
      """@current
        |trait AliasShow[A]:
        |  def show(value: A): String
        |  type inst = A
        |""".stripMargin,
      "AliasShow"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedMethod(output, "show")
        case other => fail(s"expected alias-tail delegated expansion, found $other")

      assertEquals(
        method.trailingParamss.map(_.map(_.name.toString)),
        List(List("value"), List("inst"))
      )
    }
  }

  test("parameterless inherited tails keep one stable-select forwarder and fresh evidence") {
    withExpansionInput(
      """@current
        |trait RichEmpty[A]:
        |  def empty: A
        |  type inst = A
        |  def inst(inst1: A): A = inst1
        |  type Value = A
        |""".stripMargin,
      "RichEmpty"
    ) { (input, _, companion, context) =>
      given Context = context
      assertEquals(companion, None)
      val output = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          StructuredOutcomeTestSupport.materialize(input, changes)
        case other => fail(s"expected structured parameterless tail expansion, found $other")

      val generated = output.companion.toList.flatMap(_.impl.body).collect {
        case method: DefDef => method
      }
      assertEquals(generated.map(_.name.toString), List("empty"))
      assertEquals(
        generated.head.trailingParamss.map(_.map(_.name.toString)),
        List(List("inst2"))
      )
      generated.head.rhs match
        case Select(Ident(receiver), selected) =>
          assertEquals(receiver.toString, "inst2")
          assertEquals(selected.toString, "empty")
        case other => fail(s"expected parameterless stable select, found $other")
    }
  }


  test("derives a parameterless stable-select method and creates the missing companion") {
    withExpansionInput(
      """@current
        |trait Empty[A]:
        |  def empty: A
        |""".stripMargin,
      "Empty"
    ) { (input, _, companion, context) =>
      given Context = context
      assertEquals(companion, None)
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedMethod(output, "empty")
        case other => fail("expected structured parameterless expansion")

      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(method.trailingParamss.map(_.map(_.name.toString)), List(List("inst")))
      method.rhs match
        case Select(Ident(receiver), selected) =>
          assertEquals(receiver.toString, "inst")
          assertEquals(selected.toString, "empty")
        case other => fail("expected parameterless stable select")
    }
  }

  test("parameterless generation preserves unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Default[Value]:
        |  def fallback: Value
        |
        |object Default:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Default"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "fallback")
        case other => fail("expected structured parameterless expansion")
    }
  }

  test("parameterless PreserveExisting retains a direct same-name companion method") {
    withExpansionInput(
      """@current
        |trait ExistingEmpty[A]:
        |  def empty: A
        |  type Item = A
        |  def duplicate(a: A): A = a
        |
        |object ExistingEmpty:
        |  def empty[A](using ExistingEmpty[A]): A = summon[ExistingEmpty[A]].empty
        |  val retained = 7
        |""".stripMargin,
      "ExistingEmpty"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "empty" => method
            }.size,
            1
          )
        case other => fail("expected structured parameterless expansion")
    }
  }

  test("rejects normalized infix evidence on the delegated method role") {
    withExpansionInput(
      """@current
        |trait InfixShow[A]:
        |  infix def show(a: A): String
        |""".stripMargin,
      "InfixShow"
    ) { (input, primary, _, context) =>
      given Context = context
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `InfixShow`: direct method `show` must be public, unannotated, and free of unsupported modifiers"
            )
          )
        case other => fail(s"expected controlled normalized rejection, found $other")
    }
  }

  test("rejects Scala 3.3.8 normalized erased evidence on the delegated method role") {
    if scala.util.Properties.versionNumberString == "3.3.8" then
      withExpansionInput(
        """@current
          |trait ErasedShow[A]:
          |  erased def show(a: A): String
          |""".stripMargin,
        "ErasedShow"
      ) { (input, primary, _, context) =>
        given Context = context
        new DelegatedHandler().expand(input) match
          case ExpansionOutcome.Rejected(diagnostics) =>
            assertEquals(
              diagnostics.map(_.message),
              List(
                "unsupported @delegated source shape for `ErasedShow`: direct method `show` must be public, unannotated, and free of unsupported modifiers"
              )
            )
          case other => fail(s"expected controlled normalized rejection, found $other")
      }
  }

  test("appends the generated method after preserving unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Render[Element]:
        |  def render(value: Element): Text
        |
        |object Render:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Render"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "render")
        case other => fail(s"expected structured delegated expansion, found $other")
    }
  }

  test("PreserveExisting retains the exact direct same-name companion method") {
    withExpansionInput(
      """@current
        |trait Existing[A]:
        |  def show(a: A): String
        |  type Item = A
        |  def duplicate(a: A): A = a
        |
        |object Existing:
        |  def show[A](a: A): String = "preserved"
        |  val retained = 7
        |""".stripMargin,
      "Existing"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "show" => method
            }.size,
            1
          )
        case other => fail(s"expected structured delegated expansion, found $other")
    }
  }

  test("unsupported normalized source shape rejects with the original primary and companion") {
    withExpansionInput(
      """@current
        |trait Concrete[A]:
        |  def show(a: A): String = a.toString
        |
        |object Concrete:
        |  val retained = 9
        |""".stripMargin,
      "Concrete"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `Concrete`: direct method `show` must be abstract"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(
            companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody)
          )
        case other => fail(s"expected controlled rejection, found $other")
    }
  }

  test("a late invalid inherited member rejects without a partial companion edit") {
    withExpansionInput(
      """@current
        |trait LateInvalid[A]:
        |  def show(a: A): String
        |  type Item = A
        |  def duplicate(a: A): A = a
        |  val invalid: A
        |
        |object LateInvalid:
        |  val retained = 17
        |""".stripMargin,
      "LateInvalid"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `LateInvalid`: direct body member at index 3 must be a method; found val"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody))
        case other => fail(s"expected controlled late-tail rejection, found $other")
    }
  }

  test("admits the normalized unary inherited tail for both apply-delegated source orders") {
    val apply = "com.github.dmytromitin.auxify.macros.apply"
    val delegated = "com.github.dmytromitin.auxify.macros.delegated"

    List(List(apply, delegated), List(delegated, apply)).foreach: sourceOrder =>
      withExpansionInput(
        """@current
          |trait StackedTail[A]:
          |  def show(a: A): String
          |  type Item = A
          |  def duplicate(a: A): A = a
          |
          |object StackedTail:
          |  val retained = 19
          |""".stripMargin,
        "StackedTail",
        sourceOrder
      ) { (input, _, companion, context) =>
        given Context = context
        assertEquals(input.sourceOrderedHandledAnnotationNames, sourceOrder)
        val body = input.targetBodyView.fold(
          diagnostic => fail(diagnostic.message),
          identity
        )
        assertEquals(
          body.members.map(_.kind),
          List(
            paradise3.api.ExpansionTargetBodyView.DirectMemberKind.Method,
            paradise3.api.ExpansionTargetBodyView.DirectMemberKind.Type,
            paradise3.api.ExpansionTargetBodyView.DirectMemberKind.Method
          )
        )

        val output = new DelegatedHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport.materialize(input, changes)
          case other => fail(s"expected structured stacked-tail expansion, found $other")

        val merged = output.companion.getOrElse(fail("missing merged companion"))
        assertEquals(
          merged.impl.body.collect { case method: DefDef => method.name.toString },
          companion.toList.flatMap(_.impl.body.collect {
            case method: DefDef => method.name.toString
          }) :+ "show"
        )
      }
  }

  test("admits parameterless inherited tails in both exact apply-delegated source orders") {
    val apply = "com.github.dmytromitin.auxify.macros.apply"
    val delegated = "com.github.dmytromitin.auxify.macros.delegated"

    List(List(apply, delegated), List(delegated, apply)).foreach: sourceOrder =>
      withExpansionInput(
        """@current
          |trait StackedEmpty[A]:
          |  def empty: A
          |  type inst = A
          |  def inst(inst1: A): A = inst1
          |  type Value = A
          |""".stripMargin,
        "StackedEmpty",
        sourceOrder
      ) { (input, _, companion, context) =>
        given Context = context
        assertEquals(companion, None)
        val output = new DelegatedHandler().expand(input) match
          case ExpansionOutcome.Structured(changes) =>
            StructuredOutcomeTestSupport.materialize(input, changes)
          case other => fail(s"expected structured parameterless composition, found $other")

        val methods = output.companion.toList.flatMap(_.impl.body).collect {
          case method: DefDef => method
        }
        assertEquals(methods.map(_.name.toString), List("empty"))
        val method = methods.head
        assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
        assertEquals(
          method.trailingParamss.map(_.map(_.name.toString)),
          List(List("inst2"))
        )
        method.rhs match
          case Select(Ident(receiver), selected) =>
            assertEquals(receiver.toString, "inst2")
            assertEquals(selected.toString, "empty")
          case other => fail(s"expected parameterless stable select, found $other")
        assertEquals(
          output.primary.rhs.asInstanceOf[Template].body.collect {
            case member: MemberDef => member.name.toString
          },
          List("empty", "inst", "inst", "Value")
        )
      }
  }

  test("missing primary member remains a controlled delegated rejection") {
    withExpansionInput(
      """@current
        |trait Missing[A]
        |""".stripMargin,
      "Missing"
    ) { (input, _, _, context) =>
      given Context = context
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `Missing`: requires exactly one direct body member; found 0"
            )
          )
        case other => fail(s"expected controlled missing-member rejection, found $other")
    }
  }


  test("parameterless tail bridge failure rolls back the exact composition cohort") {
    withExpansionInput(
      """@current
        |trait Empty[A]:
        |  def empty: A
        |  type Item = A
        |  def duplicate(value: A): A = value
        |
        |object Empty:
        |  val retained = 13
        |""".stripMargin,
      "Empty",
      List(
        "com.github.dmytromitin.auxify.macros.apply",
        "com.github.dmytromitin.auxify.macros.delegated"
      )
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body

      DelegatedHandler.expandWithLowering(input): (_, _) =>
        Left(
          DelegatedForwardingMethodPeerBridge.Failure(
            "EXACT_FORWARDING_LOWERING_FAILED",
            "controlled parameterless bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_FORWARDING_LOWERING_FAILED: controlled parameterless bridge failure")
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody))
        case other => fail("expected controlled parameterless bridge rejection")
    }
  }

  test("bridge failure becomes one atomic rejection without a partial companion edit") {
    withExpansionInput(
      """@current
        |trait Show[A]:
        |  def show(a: A): String
        |  type Item = A
        |  def duplicate(a: A): A = a
        |
        |object Show:
        |  val retained = 11
        |""".stripMargin,
      "Show"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val originalCompanionBody = companion.getOrElse(fail("missing companion")).impl.body

      DelegatedHandler.expandWithLowering(input): (_, _) =>
        Left(
          DelegatedForwardingMethodPeerBridge.Failure(
            "EXACT_FORWARDING_LOWERING_FAILED",
            "controlled bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_FORWARDING_LOWERING_FAILED: controlled bridge failure")
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(
            companion.getOrElse(fail("missing companion")).impl.body.eq(originalCompanionBody)
          )
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  test("rejects tail composition outside the exact apply-delegated participant cohort") {
    withExpansionInput(
      """@current
        |trait ExtraParticipant[A]:
        |  def show(a: A): String
        |  type Item = A
        |""".stripMargin,
      "ExtraParticipant",
      List(
        "com.github.dmytromitin.auxify.macros.apply",
        "com.github.dmytromitin.auxify.macros.delegated",
        "com.github.dmytromitin.auxify.macros.syntax"
      )
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated composition shape for `ExtraParticipant`: inherited concrete tails require exactly the @apply and @delegated participant cohort"
            )
          )
          assertEquals(diagnostics.map(_.pos), List(input.currentAnnotation.sourcePos))
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assertEquals(companion, None)
        case other => fail(s"expected closed-cohort rejection, found $other")
    }
  }

  test("rejects an invalid unary primary before considering its valid inherited tail") {
    withExpansionInput(
      """@current
        |trait InvalidPrimary[A]:
        |  def show(a: A): List[String]
        |  type Item = A
        |  def duplicate(a: A): A = a
        |""".stripMargin,
      "InvalidPrimary",
      List(
        "com.github.dmytromitin.auxify.macros.delegated",
        "com.github.dmytromitin.auxify.macros.apply"
      )
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated source shape for `InvalidPrimary`: direct method `show` result type must be one unqualified named type"
            )
          )
          assert(diagnostics.head.pos.span.exists, clue(diagnostics))
          assertEquals(diagnostics.head.pos.sourcePos.line, 2)
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assertEquals(companion, None)
        case other => fail(s"expected invalid-primary rejection, found $other")
    }
  }

  test("composition keeps delegated evidence collision-fresh against inherited term roles") {
    withExpansionInput(
      """@current
        |trait FreshStacked[Element]:
        |  def render(inst: Element): Text
        |  type inst = Element
        |  def inst1(inst2: Element): Element = inst2
        |""".stripMargin,
      "FreshStacked",
      List(
        "com.github.dmytromitin.auxify.macros.apply",
        "com.github.dmytromitin.auxify.macros.delegated"
      )
    ) { (input, _, _, context) =>
      given Context = context
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          generatedMethod(StructuredOutcomeTestSupport.materialize(input, changes), "render")
        case other => fail(s"expected fresh stacked expansion, found $other")

      assertEquals(
        method.trailingParamss.map(_.map(_.name.toString)),
        List(List("inst"), List("inst3"))
      )
    }
  }

  test("rejects parameterless tails outside the exact apply-delegated participant cohort") {
    withExpansionInput(
      """@current
        |trait ExtraParameterlessParticipant[A]:
        |  def empty: A
        |  type Item = A
        |""".stripMargin,
      "ExtraParameterlessParticipant",
      List(
        "com.github.dmytromitin.auxify.macros.apply",
        "com.github.dmytromitin.auxify.macros.delegated",
        "com.github.dmytromitin.auxify.macros.syntax"
      )
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @delegated composition shape for `ExtraParameterlessParticipant`: inherited concrete tails require exactly the @apply and @delegated participant cohort"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assertEquals(companion, None)
        case other => fail(s"expected parameterless closed-cohort rejection, found $other")
    }
  }

  test("parameterless composition freshens evidence against a primary named inst") {
    withExpansionInput(
      """@current
        |trait FreshParameterless[Element]:
        |  def inst: Element
        |  type inst = Element
        |  def inst1(inst1: Element): Element = inst1
        |""".stripMargin,
      "FreshParameterless",
      List(
        "com.github.dmytromitin.auxify.macros.delegated",
        "com.github.dmytromitin.auxify.macros.apply"
      )
    ) { (input, _, _, context) =>
      given Context = context
      val method = new DelegatedHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          generatedMethod(StructuredOutcomeTestSupport.materialize(input, changes), "inst")
        case other => fail(s"expected fresh parameterless expansion, found $other")

      assertEquals(
        method.trailingParamss.map(_.map(_.name.toString)),
        List(List("inst2"))
      )
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String,
      sourceOrderedHandledAnnotationNames: List[String] = Nil
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}DelegatedHandlerFixture.scala", source)
    given Context = ContextBase().initialCtx.fresh.setCompilationUnit(unit)
    val stats = new Parsers.Parser(unit.source).parse() match
      case PackageDef(_, values) => values
      case value: TypeDef => List(value)
      case other => fail(s"missing package stats in $other")
    val primary = stats.collectFirst { case value: TypeDef => value }
      .getOrElse(fail(s"missing primary TypeDef in $stats"))
    val companion = stats.collectFirst { case value: ModuleDef => value }
    val currentAnnotation = Trees.mods(primary).annotations.head
    run(
      paradise3.api.ExpansionInputTestFactory(
        "com.github.dmytromitin.auxify.macros.delegated",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation),
        sourceOrderedHandledAnnotationNames
      ),
      primary,
      companion,
      summon[Context]
    )

  private def generatedMethod(
      output: StructuredExpansionOutput,
      methodName: String
  )(using Context): DefDef =
    output.companion
      .toList
      .flatMap(_.impl.body)
      .collectFirst {
        case method: DefDef if method.name.toString == methodName => method
      }
      .getOrElse(fail(s"missing generated $methodName method"))
