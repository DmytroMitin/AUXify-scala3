package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.{Context, ContextBase}

import scala.annotation.nowarn
import scala.meta.*

@nowarn("cat=deprecation")
class InstanceDefinitionBuilderSuite extends munit.FunSuite:
  test("lowers the canonical factory through C014 with generated provenance") {
    withContext:
      val lowered = InstanceDefinitionBuilder
        .lower(
          shape(
            traitName = "Monoid",
            typeParameterName = "A",
            parameterlessMethodName = "empty",
            binaryMethodName = "combine",
            firstParameterName = "a",
            secondParameterName = "a1",
            parameterlessCarrierName = "emptyValue",
            binaryCarrierName = "combineFunction"
          )
        )
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedMonoidInstance.scala")
      assertEquals(
        lowered.generatedSource,
        "def instance[A](emptyValue: => A, combineFunction: (A, A) => A): Monoid[A] = new Monoid[A] { override def empty: A = emptyValue; override def combine(a: A, a1: A): A = combineFunction(a, a1) }"
      )
      assertEquals(lowered.tree.name.toString, "instance")
      assert(lowered.tree.source.exists, clue(lowered.tree))
      assertEquals(lowered.tree.source.path, lowered.virtualSourceName)
      assertEquals(lowered.tree.source.content.mkString, lowered.generatedSource)
  }

  test("lowers coherently renamed source-derived names through C014") {
    withContext:
      val lowered = InstanceDefinitionBuilder
        .lower(
          shape(
            traitName = "Choice",
            typeParameterName = "Element",
            parameterlessMethodName = "fallback",
            binaryMethodName = "select",
            firstParameterName = "left",
            secondParameterName = "right",
            parameterlessCarrierName = "emptyValue",
            binaryCarrierName = "combineFunction"
          )
        )
        .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedChoiceInstance.scala")
      assertEquals(lowered.tree.name.toString, "instance")
      assert(lowered.generatedSource.contains("Choice[Element]"), clue(lowered.generatedSource))
      assert(
        lowered.generatedSource.contains("override def fallback"),
        clue(lowered.generatedSource)
      )
      assert(
        lowered.generatedSource.contains("override def select"),
        clue(lowered.generatedSource)
      )
  }

  test("builds the canonical typed instance factory") {
    val definition = InstanceDefinitionBuilder.definition(
      shape(
        traitName = "Monoid",
        typeParameterName = "A",
        parameterlessMethodName = "empty",
        binaryMethodName = "combine",
        firstParameterName = "a",
        secondParameterName = "a1",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )

    assertEquals(
      definition.syntax,
      """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): Monoid[A] = new Monoid[A] {
        |  override def empty: A = emptyValue
        |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTraitName = "Monoid",
      expectedTypeParameterName = "A",
      expectedParameterlessMethodName = "empty",
      expectedBinaryMethodName = "combine",
      expectedFirstParameterName = "a",
      expectedSecondParameterName = "a1",
      expectedParameterlessCarrierName = "emptyValue",
      expectedBinaryCarrierName = "combineFunction"
    )
  }

  test("builds a coherently renamed typed instance factory") {
    val definition = InstanceDefinitionBuilder.definition(
      shape(
        traitName = "Choice",
        typeParameterName = "Element",
        parameterlessMethodName = "fallback",
        binaryMethodName = "select",
        firstParameterName = "left",
        secondParameterName = "right",
        parameterlessCarrierName = "emptyValue",
        binaryCarrierName = "combineFunction"
      )
    )

    assertEquals(
      definition.syntax,
      """def instance[Element](emptyValue: => Element, combineFunction: (Element, Element) => Element): Choice[Element] = new Choice[Element] {
        |  override def fallback: Element = emptyValue
        |  override def select(left: Element, right: Element): Element = combineFunction(left, right)
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTraitName = "Choice",
      expectedTypeParameterName = "Element",
      expectedParameterlessMethodName = "fallback",
      expectedBinaryMethodName = "select",
      expectedFirstParameterName = "left",
      expectedSecondParameterName = "right",
      expectedParameterlessCarrierName = "emptyValue",
      expectedBinaryCarrierName = "combineFunction"
    )
  }

  test("uses collision-free carrier names coherently throughout the factory") {
    val definition = InstanceDefinitionBuilder.definition(
      shape(
        traitName = "Collision",
        typeParameterName = "Element",
        parameterlessMethodName = "emptyValue",
        binaryMethodName = "merge",
        firstParameterName = "combineFunction",
        secondParameterName = "right",
        parameterlessCarrierName = "emptyValue1",
        binaryCarrierName = "combineFunction1"
      )
    )

    assertEquals(
      definition.syntax,
      """def instance[Element](emptyValue1: => Element, combineFunction1: (Element, Element) => Element): Collision[Element] = new Collision[Element] {
        |  override def emptyValue: Element = emptyValue1
        |  override def merge(combineFunction: Element, right: Element): Element = combineFunction1(combineFunction, right)
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTraitName = "Collision",
      expectedTypeParameterName = "Element",
      expectedParameterlessMethodName = "emptyValue",
      expectedBinaryMethodName = "merge",
      expectedFirstParameterName = "combineFunction",
      expectedSecondParameterName = "right",
      expectedParameterlessCarrierName = "emptyValue1",
      expectedBinaryCarrierName = "combineFunction1"
    )
  }

  private def shape(
      traitName: String,
      typeParameterName: String,
      parameterlessMethodName: String,
      binaryMethodName: String,
      firstParameterName: String,
      secondParameterName: String,
      parameterlessCarrierName: String,
      binaryCarrierName: String
  ): InstanceSourceShapeDecoder.SourceShape =
    InstanceSourceShapeDecoder.SourceShape(
      traitName,
      typeParameterName,
      parameterlessMethodName,
      binaryMethodName,
      firstParameterName,
      secondParameterName,
      parameterlessCarrierName,
      binaryCarrierName
    )

  private def assertTypedTopology(
      definition: Defn.Def,
      expectedTraitName: String,
      expectedTypeParameterName: String,
      expectedParameterlessMethodName: String,
      expectedBinaryMethodName: String,
      expectedFirstParameterName: String,
      expectedSecondParameterName: String,
      expectedParameterlessCarrierName: String,
      expectedBinaryCarrierName: String
  ): Unit =
    assertEquals(definition.name.value, "instance")
    assertEquals(definition.paramClauseGroups.size, 1)
    val group = definition.paramClauseGroups.head
    assertEquals(
      group.tparamClause.values.map(_.name.value),
      List(expectedTypeParameterName)
    )
    assertEquals(group.paramClauses.size, 1)
    group.paramClauses.head.values match
      case List(parameterlessCarrier, binaryCarrier) =>
        assertEquals(
          parameterlessCarrier.name.value,
          expectedParameterlessCarrierName
        )
        parameterlessCarrier.decltpe match
          case Some(byName: Type.ByName) =>
            assertEquals(byName.tpe.syntax, expectedTypeParameterName)
          case other => fail(s"expected by-name carrier type, found $other")
        assertEquals(binaryCarrier.name.value, expectedBinaryCarrierName)
        binaryCarrier.decltpe match
          case Some(function: Type.Function) =>
            assertEquals(
              function.paramClause.values.map(_.syntax),
              List(expectedTypeParameterName, expectedTypeParameterName)
            )
            assertEquals(function.res.syntax, expectedTypeParameterName)
          case other => fail(s"expected binary function carrier type, found $other")
      case other => fail(s"expected two factory carriers, found $other")

    assertAppliedTarget(
      definition.decltpe,
      expectedTraitName,
      expectedTypeParameterName
    )
    definition.body match
      case implementation: Term.NewAnonymous =>
        assertEquals(implementation.templ.inits.size, 1)
        assertEquals(
          implementation.templ.inits.head.tpe.syntax,
          s"$expectedTraitName[$expectedTypeParameterName]"
        )
        implementation.templ.stats match
          case List(parameterlessOverride: Defn.Def, binaryOverride: Defn.Def) =>
            assert(parameterlessOverride.mods.exists(_.isInstanceOf[Mod.Override]))
            assertEquals(
              parameterlessOverride.name.value,
              expectedParameterlessMethodName
            )
            assertEquals(parameterlessOverride.paramClauseGroups, Nil)
            assertEquals(
              parameterlessOverride.decltpe.map(_.syntax),
              Some(expectedTypeParameterName)
            )
            parameterlessOverride.body match
              case Term.Name(value) =>
                assertEquals(value, expectedParameterlessCarrierName)
              case other => fail(s"expected parameterless carrier reference, found $other")

            assert(binaryOverride.mods.exists(_.isInstanceOf[Mod.Override]))
            assertEquals(binaryOverride.name.value, expectedBinaryMethodName)
            val binaryClause = binaryOverride.paramClauseGroups.head.paramClauses.head
            assertEquals(
              binaryClause.values.map(_.name.value),
              List(expectedFirstParameterName, expectedSecondParameterName)
            )
            assertEquals(
              binaryClause.values.map(_.decltpe.map(_.syntax)),
              List.fill(2)(Some(expectedTypeParameterName))
            )
            assertEquals(
              binaryOverride.decltpe.map(_.syntax),
              Some(expectedTypeParameterName)
            )
            binaryOverride.body match
              case Term.Apply(Term.Name(function), arguments) =>
                assertEquals(function, expectedBinaryCarrierName)
                assertEquals(
                  arguments.map(_.syntax),
                  List(expectedFirstParameterName, expectedSecondParameterName)
                )
              case other => fail(s"expected binary carrier application, found $other")
          case other => fail(s"expected two override definitions, found $other")
      case other => fail(s"expected anonymous implementation, found $other")

  private def assertAppliedTarget(
      target: Option[Type],
      expectedTraitName: String,
      expectedTypeParameterName: String
  ): Unit =
    target match
      case Some(applied: Type.Apply) =>
        assertEquals(applied.tpe.syntax, expectedTraitName)
        assertEquals(
          applied.argClause.values.map(_.syntax),
          List(expectedTypeParameterName)
        )
      case other => fail(s"expected applied target, found $other")

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
