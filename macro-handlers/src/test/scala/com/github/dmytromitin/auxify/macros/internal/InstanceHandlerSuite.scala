package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.parsing.Parsers

import paradise3.api.{
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome
}

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

class InstanceHandlerSuite extends munit.FunSuite:
  test("implements the current protocol with the public instance annotation") {
    val handler: ExpansionHandler = new InstanceHandler
    assertEquals(
      handler.annotationName,
      "com.github.dmytromitin.auxify.macros.instance"
    )
  }

  test("derives and places the canonical instance factory") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |""".stripMargin,
      "Monoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(method.name.toString, "instance")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
    }
  }

  test("derives and places the canonical abstract-type-member instance factory") {
    withExpansionInput(
      """@current
        |trait HasOut[A]:
        |  type Out
        |""".stripMargin,
      "HasOut"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured abstract-type factory, found $other")

      assertEquals(method.name.toString, "instance")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A", "Out0"))
      assertEquals(method.trailingParamss, Nil)
    }
  }

  test("dispatches an exact one-method curried source only to the curried family") {
    withExpansionInput(
      """@current
        |trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "Curried"
    ) { (input, _, _, context) =>
      given Context = context
      var captured: Option[InstanceCurriedMethodSourceShapeDecoder.SourceShape] = None
      val outcome = InstanceHandler.expandWithLowering(input): (shape, loweringContext) =>
        shape match
          case InstanceHandler.SourceShape.CurriedMethod(value) =>
            captured = Some(value)
            InstanceDefinitionBuilder.lower(
              InstanceSourceShapeDecoder.SourceShape(
                "Monoid",
                "A",
                "empty",
                "combine",
                "a",
                "b",
                "emptyValue",
                "combineFunction"
              )
            )(using loweringContext)
          case other => fail(s"expected curried family dispatch, found $other")

      outcome match
        case ExpansionOutcome.Structured(_) =>
          assertEquals(
            captured,
            Some(
              InstanceCurriedMethodSourceShapeDecoder.SourceShape(
                "Curried",
                "A",
                "combine",
                "a",
                "b",
                "combineFunction"
              )
            )
          )
        case other => fail(s"expected structured curried dispatch, found $other")
    }
  }

  test("derives and places the canonical curried-method instance factory") {
    withExpansionInput(
      """@current
        |trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |""".stripMargin,
      "Curried"
    ) { (input, _, companion, context) =>
      given Context = context
      assertEquals(companion, None)
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          generatedInstance(StructuredOutcomeTestSupport.materialize(input, changes))
        case other => fail(s"expected structured curried expansion, found $other")

      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      method.trailingParamss match
        case List(List(carrier: ValDef)) =>
          assertEquals(carrier.name.toString, "combineFunction")
          carrier.tpt match
            case Function(
                  List(Ident(first)),
                  Function(List(Ident(second)), Ident(result))
                ) =>
              assertEquals(
                List(first.toString, second.toString, result.toString),
                List("A", "A", "A")
              )
            case other => fail(s"expected nested unary carrier, found $other")
        case other => fail(s"expected one strict carrier, found $other")
      method.rhs match
        case New(template: Template) =>
          template.body match
            case List(overrideMethod: DefDef) =>
              assertEquals(
                overrideMethod.trailingParamss.map(_.map(_.name.toString)),
                List(List("a"), List("b"))
              )
              overrideMethod.rhs match
                case Apply(
                      Apply(Ident(callee), List(Ident(first))),
                      List(Ident(second))
                    ) =>
                  assertEquals(callee.toString, "combineFunction")
                  assertEquals(first.toString, "a")
                  assertEquals(second.toString, "b")
                case other => fail(s"expected successive applications, found $other")
            case other => fail(s"expected one anonymous override, found $other")
        case other => fail(s"expected anonymous implementation, found $other")
    }
  }

  test("curried-method generation preserves renamed and collision-freshened roles") {
    withExpansionInput(
      """@current
        |trait Chain[Element]:
        |  def combineFunction(left: Element)(right: Element): Element
        |""".stripMargin,
      "Chain"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          generatedInstance(StructuredOutcomeTestSupport.materialize(input, changes))
        case other => fail(s"expected renamed curried expansion, found $other")

      assertEquals(method.leadingTypeParams.map(_.name.toString), List("Element"))
      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("combineFunction1")
      )
      assert(method.rhs.toString.contains("combineFunction1"), clue(method.rhs))
      assert(method.rhs.toString.contains("left"), clue(method.rhs))
      assert(method.rhs.toString.contains("right"), clue(method.rhs))
    }
  }

  test("curried-method generation preserves unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |
        |object Curried:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Curried"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val names = output.companion.toList.flatMap(_.impl.body.collect {
            case member: MemberDef => member.name.toString
          })
          assertEquals(names, originalNames :+ "instance")
        case other => fail(s"expected preserved curried companion, found $other")
    }
  }

  test("curried-method PreserveExisting retains a direct instance member") {
    withExpansionInput(
      """@current
        |trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |
        |object Curried:
        |  def instance[A](f: A => A => A): Curried[A] = ???
        |  val retained = 7
        |""".stripMargin,
      "Curried"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "instance" => method
            }.size,
            1
          )
        case other => fail(s"expected preserved direct instance, found $other")
    }
  }

  test("curried bridge failure rolls back without partial companion mutation") {
    withExpansionInput(
      """@current
        |trait Curried[A]:
        |  def combine(a: A)(b: A): A
        |
        |object Curried:
        |  val retained = 11
        |""".stripMargin,
      "Curried"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing fixture companion"))
      val originalCompanionBody = existing.impl.body

      InstanceHandler.expandWithLowering(input): (_, _) =>
        Left(
          InstanceFactoryPeerBridge.Failure(
            "EXACT_RAW_LOWERING_FAILED",
            "controlled curried bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_RAW_LOWERING_FAILED: controlled curried bridge failure")
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(existing.impl.body.eq(originalCompanionBody), clue(existing.impl.body))
        case other => fail(s"expected controlled curried rollback, found $other")
    }
  }

  test("derives renamed abstract-type roles and collision-free type parameters") {
    withExpansionInput(
      """@current
        |trait Container[Element0]:
        |  type Element
        |""".stripMargin,
      "Container"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          generatedInstance(StructuredOutcomeTestSupport.materialize(input, changes))
        case other => fail(s"expected renamed abstract-type factory, found $other")

      assertEquals(
        method.leadingTypeParams.map(_.name.toString),
        List("Element0", "Element1")
      )
      method.tpt match
        case refinement: RefinedTypeTree =>
          assert(refinement.toString.contains("Container"), clue(refinement))
          assert(refinement.toString.contains("Element"), clue(refinement))
          assert(refinement.toString.contains("Element1"), clue(refinement))
        case other => fail(s"expected renamed refined result, found $other")
    }
  }

  test("abstract-type family preserves unrelated existing companion members") {
    withExpansionInput(
      """@current
        |trait HasOut[A]:
        |  type Out
        |
        |object HasOut:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "HasOut"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "instance")
        case other => fail(s"expected structured abstract-type expansion, found $other")
    }
  }

  test("abstract-type family PreserveExisting retains a direct instance member") {
    withExpansionInput(
      """@current
        |trait ExistingOut[A]:
        |  type Out
        |
        |object ExistingOut:
        |  def instance[A, Out0]: ExistingOut[A] { type Out = Out0 } = ???
        |  val retained = 7
        |""".stripMargin,
      "ExistingOut"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "instance" => method
            }.size,
            1
          )
        case other => fail(s"expected structured abstract-type expansion, found $other")
    }
  }

  test("one abstract val remains rejected before C060 lowering") {
    withExpansionInput(
      """@current
        |trait HasValue[A]:
        |  val value: A
        |""".stripMargin,
      "HasValue"
    ) { (input, _, _, context) =>
      given Context = context
      var lowerCalled = false
      InstanceHandler.expandWithLowering(input): (_, _) =>
        lowerCalled = true
        fail("one-abstract-val source must not reach peer lowering")
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @instance source shape for `HasValue`: requires exactly two direct body members; found 1"
            )
          )
          assert(!lowerCalled)
        case other => fail(s"expected controlled abstract-val rejection, found $other")
    }
  }

  test("abstract-type bridge failure rolls back without partial companion mutation") {
    withExpansionInput(
      """@current
        |trait HasOut[A]:
        |  type Out
        |
        |object HasOut:
        |  val retained = 11
        |""".stripMargin,
      "HasOut"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing fixture companion"))
      val originalCompanionBody = existing.impl.body

      InstanceHandler.expandWithLowering(input): (_, _) =>
        Left(
          InstanceFactoryPeerBridge.Failure(
            "EXACT_RAW_LOWERING_FAILED",
            "controlled abstract-type bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "EXACT_RAW_LOWERING_FAILED: controlled abstract-type bridge failure"
            )
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(existing.impl.body.eq(originalCompanionBody), clue(existing.impl.body))
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  test("admits a third-position concrete type alias without changing the factory roles") {
    withExpansionInput(
      """@current
        |trait WrappedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |""".stripMargin,
      "WrappedMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(method.name.toString, "instance")
      assertEquals(method.leadingTypeParams.map(_.name.toString), List("A"))
      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredTypeDefinitions = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: TypeDef =>
              authoredTypeDefinitions += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(authoredTypeDefinitions.toList, Nil)
    }
  }

  test("inherits a third-position concrete parameterless method without authoring it") {
    withExpansionInput(
      """@current
        |trait ZeroMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zero: A = empty
        |""".stripMargin,
      "ZeroMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredMethods = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: DefDef => authoredMethods += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(
        authoredMethods.toList.filterNot(_ == "<init>").sorted,
        List("combine", "empty")
      )
    }
  }

  test("inherits a third-position concrete binary method without authoring it") {
    withExpansionInput(
      """@current
        |trait BinaryDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def combineAgain(a: A, a1: A): A = combine(a, a1)
        |""".stripMargin,
      "BinaryDerivedMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredMethods = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: DefDef => authoredMethods += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(
        authoredMethods.toList.filterNot(_ == "<init>").sorted,
        List("combine", "empty")
      )
    }
  }

  test("inherits a larger-arity concrete method without authoring it") {
    withExpansionInput(
      """@current
        |trait LargerDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def fold5(a: A, b: A, c: A, d: A, e: A): A =
        |    combine(combine(combine(combine(a, b), c), d), e)
        |""".stripMargin,
      "LargerDerivedMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredMethods = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: DefDef => authoredMethods += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(
        authoredMethods.toList.filterNot(_ == "<init>").sorted,
        List("combine", "empty")
      )
    }
  }

  test("inherits multiple mixed-arity concrete methods without authoring them") {
    withExpansionInput(
      """@current
        |trait RichDerivedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  def zeroLike: A = empty
        |  def twice(a: A): A = combine(a, a)
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "RichDerivedMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredMethods = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: DefDef => authoredMethods += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(
        authoredMethods.toList.filterNot(_ == "<init>").sorted,
        List("combine", "empty")
      )
    }
  }

  test("inherits an interleaved heterogeneous tail without authoring it") {
    withExpansionInput(
      """@current
        |trait RichTypedMonoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  type Item = A
        |  def twice(a: A): A = combine(a, a)
        |  type Value = A
        |  def fold3(a: A, b: A, c: A): A = combine(combine(a, b), c)
        |""".stripMargin,
      "RichTypedMonoid"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue", "combineFunction")
      )
      val authoredMethods = scala.collection.mutable.ListBuffer.empty[String]
      val traverser = new untpd.UntypedTreeTraverser:
        override def traverse(tree: untpd.Tree)(using Context): Unit =
          tree match
            case definition: DefDef => authoredMethods += definition.name.toString
            case _ => ()
          traverseChildren(tree)
      traverser.traverse(method.rhs)
      assertEquals(
        authoredMethods.toList.filterNot(_ == "<init>").sorted,
        List("combine", "empty")
      )
    }
  }

  test("rejects an infix concrete alias through normalized type-member modifiers") {
    withExpansionInput(
      """@current
        |trait InfixItem[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |  infix type Item = A
        |""".stripMargin,
      "InfixItem"
    ) { (input, primary, _, context) =>
      given Context = context
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List(
              "unsupported @instance source shape for `InfixItem`: inherited concrete type alias `Item` must be public, unannotated, and free of unsupported modifiers"
            )
          )
        case other => fail(s"expected controlled normalized rejection, found $other")
    }
  }

  test("rejects normalized infix evidence on every instance method role") {
    List(
      (
        "InfixEmptyMonoid",
        """@current
          |trait InfixEmptyMonoid[A]:
          |  infix def empty: A
          |  def combine(a: A, a1: A): A
          |""".stripMargin,
        "direct method `empty`"
      ),
      (
        "InfixCombineMonoid",
        """@current
          |trait InfixCombineMonoid[A]:
          |  def empty: A
          |  infix def combine(a: A, a1: A): A
          |""".stripMargin,
        "direct method `combine`"
      ),
      (
        "InfixConcreteMonoid",
        """@current
          |trait InfixConcreteMonoid[A]:
          |  def empty: A
          |  def combine(a: A, a1: A): A
          |  infix def twice(a: A): A = combine(a, a)
          |""".stripMargin,
        "inherited concrete method `twice`"
      ),
      (
        "InfixZeroMonoid",
        """@current
          |trait InfixZeroMonoid[A]:
          |  def empty: A
          |  def combine(a: A, a1: A): A
          |  infix def zero: A = empty
          |""".stripMargin,
        "inherited concrete method `zero`"
      )
    ).foreach: (traitName, source, role) =>
      withExpansionInput(
        source,
        traitName
      ) { (input, primary, _, context) =>
        given Context = context
        new InstanceHandler().expand(input) match
          case ExpansionOutcome.Rejected(diagnostics) =>
            assertEquals(
              diagnostics.map(_.message),
              List(
                s"unsupported @instance source shape for `$traitName`: $role must be public, unannotated, and free of unsupported modifiers"
              )
            )
          case other => fail(s"expected controlled normalized rejection, found $other")
      }
  }

  test("rejects Scala 3.3.8 normalized erased evidence on every instance method role") {
    if scala.util.Properties.versionNumberString == "3.3.8" then
      List(
        (
          "ErasedEmptyMonoid",
          """@current
            |trait ErasedEmptyMonoid[A]:
            |  erased def empty: A
            |  def combine(a: A, a1: A): A
            |""".stripMargin,
          "direct method `empty`"
        ),
        (
          "ErasedCombineMonoid",
          """@current
            |trait ErasedCombineMonoid[A]:
            |  def empty: A
            |  erased def combine(a: A, a1: A): A
            |""".stripMargin,
          "direct method `combine`"
        ),
        (
          "ErasedConcreteMonoid",
          """@current
            |trait ErasedConcreteMonoid[A]:
            |  def empty: A
            |  def combine(a: A, a1: A): A
            |  erased def twice(a: A): A = combine(a, a)
            |""".stripMargin,
          "inherited concrete method `twice`"
        ),
        (
          "ErasedZeroMonoid",
          """@current
            |trait ErasedZeroMonoid[A]:
            |  def empty: A
            |  def combine(a: A, a1: A): A
            |  erased def zero: A = empty
            |""".stripMargin,
          "inherited concrete method `zero`"
        )
      ).foreach: (traitName, source, role) =>
        withExpansionInput(source, traitName) { (input, primary, _, context) =>
          given Context = context
          new InstanceHandler().expand(input) match
            case ExpansionOutcome.Rejected(diagnostics) =>
              assertEquals(
                diagnostics.map(_.message),
                List(
                  s"unsupported @instance source shape for `$traitName`: $role must be public, unannotated, and free of unsupported modifiers"
                )
              )
            case other => fail(s"expected controlled normalized rejection, found $other")
        }
  }

  test("derives coherently renamed source names") {
    withExpansionInput(
      """@current
        |trait Choice[Element]:
        |  def fallback: Element
        |  def select(left: Element, right: Element): Element
        |""".stripMargin,
      "Choice"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(method.leadingTypeParams.map(_.name.toString), List("Element"))
      method.tpt match
        case AppliedTypeTree(Ident(target), List(Ident(argument))) =>
          assertEquals(target.toString, "Choice")
          assertEquals(argument.toString, "Element")
        case other => fail(s"expected renamed applied target, found $other")
      assert(method.rhs.toString.contains("fallback"), clue(method.rhs))
      assert(method.rhs.toString.contains("select"), clue(method.rhs))
    }
  }

  test("uses decoder-selected collision-free carrier names in the lowered factory") {
    withExpansionInput(
      """@current
        |trait Collision[Element]:
        |  def emptyValue: Element
        |  def merge(combineFunction: Element, right: Element): Element
        |""".stripMargin,
      "Collision"
    ) { (input, _, _, context) =>
      given Context = context
      val method = new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          generatedInstance(output)
        case other => fail(s"expected structured instance expansion, found $other")

      assertEquals(
        method.trailingParamss.flatten.map(_.name.toString),
        List("emptyValue1", "combineFunction1")
      )
    }
  }

  test("appends the factory after preserving unrelated companion members") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Monoid:
        |  val before = 41
        |  object Nested
        |  val after = 43
        |""".stripMargin,
      "Monoid"
    ) { (input, _, companion, context) =>
      given Context = context
      val originalNames = companion.toList.flatMap(_.impl.body.collect {
        case member: MemberDef => member.name.toString
      })
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val merged = output.companion.getOrElse(fail("missing merged companion"))
          val names = merged.impl.body.collect {
            case member: MemberDef => member.name.toString
          }
          assertEquals(names, originalNames :+ "instance")
        case other => fail(s"expected structured instance expansion, found $other")
    }
  }

  test("PreserveExisting retains an existing direct instance member unchanged") {
    withExpansionInput(
      """@current
        |trait Existing[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Existing:
        |  def instance[A](value: A): Existing[A] = ???
        |  val retained = 7
        |""".stripMargin,
      "Existing"
    ) { (input, _, companion, context) =>
      given Context = context
      val original = companion.getOrElse(fail("missing fixture companion"))
      val originalBody = original.impl.body
      new InstanceHandler().expand(input) match
        case ExpansionOutcome.Structured(changes) =>
          val output = StructuredOutcomeTestSupport.materialize(input, changes)
          val preserved = output.companion.getOrElse(fail("missing preserved companion"))
          assert(preserved.eq(original), clue(preserved))
          assert(preserved.impl.body.eq(originalBody), clue(preserved.impl.body))
          assertEquals(
            preserved.impl.body.collect {
              case method: DefDef if method.name.toString == "instance" => method
            }.size,
            1
          )
        case other => fail(s"expected structured instance expansion, found $other")
    }
  }

  test("bridge failure becomes one atomic rejection without partial companion mutation") {
    withExpansionInput(
      """@current
        |trait Monoid[A]:
        |  def empty: A
        |  def combine(a: A, a1: A): A
        |
        |object Monoid:
        |  val retained = 11
        |""".stripMargin,
      "Monoid"
    ) { (input, primary, companion, context) =>
      given Context = context
      val originalTemplate = primary.rhs
      val existing = companion.getOrElse(fail("missing fixture companion"))
      val originalCompanionBody = existing.impl.body

      InstanceHandler.expandWithLowering(input): (_, _) =>
        Left(
          InstanceFactoryPeerBridge.Failure(
            "EXACT_RAW_LOWERING_FAILED",
            "controlled bridge failure"
          )
        )
      match
        case ExpansionOutcome.Rejected(diagnostics) =>
          assertEquals(
            diagnostics.map(_.message),
            List("EXACT_RAW_LOWERING_FAILED: controlled bridge failure")
          )
          assert(primary.rhs.eq(originalTemplate), clue(primary.rhs))
          assert(existing.impl.body.eq(originalCompanionBody), clue(existing.impl.body))
        case other => fail(s"expected controlled bridge rejection, found $other")
    }
  }

  private def withExpansionInput[A](
      source: String,
      className: String
  )(run: (ExpansionInput, TypeDef, Option[ModuleDef], Context) => A): A =
    val unit = CompilationUnit(s"${className}InstanceHandlerFixture.scala", source)
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
        "com.github.dmytromitin.auxify.macros.instance",
        primary,
        companion,
        Set(className),
        Some(currentAnnotation)
      ),
      primary,
      companion,
      summon[Context]
    )

  private def generatedInstance(
      output: StructuredExpansionOutput
  )(using Context): DefDef =
    output.companion
      .toList
      .flatMap(_.impl.body)
      .collectFirst {
        case method: DefDef if method.name.toString == "instance" => method
      }
      .getOrElse(fail("missing generated instance method"))
