package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*

@nowarn("cat=deprecation")
class AuxDefinitionBuilderSuite extends munit.FunSuite:
  test("builds the canonical typed Aux definition") {
    val definition = AuxDefinitionBuilder.definition(
      AuxSourceShapeDecoder.Shape(
        typeClassName = "Add",
        firstTypeParameterName = "N",
        secondTypeParameterName = "M",
        upperBoundTypeName = "Nat",
        resultTypeMemberName = "Out",
        generatedResultParameterName = "Out0"
      )
    )

    assertEquals(
      definition.syntax,
      """type Aux[N <: Nat, M <: Nat, Out0 <: Nat] = Add[N, M] {
        |  type Out = Out0
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTypeClassName = "Add",
      expectedTypeParameterNames = List("N", "M", "Out0"),
      expectedUpperBoundName = "Nat",
      expectedResultMemberName = "Out"
    )
  }

  test("builds a coherently renamed typed Aux definition") {
    val definition = AuxDefinitionBuilder.definition(
      AuxSourceShapeDecoder.Shape(
        typeClassName = "Combine",
        firstTypeParameterName = "Left",
        secondTypeParameterName = "Right",
        upperBoundTypeName = "Natural",
        resultTypeMemberName = "Result",
        generatedResultParameterName = "Result0"
      )
    )

    assertEquals(
      definition.syntax,
      """type Aux[Left <: Natural, Right <: Natural, Result0 <: Natural] = Combine[Left, Right] {
        |  type Result = Result0
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTypeClassName = "Combine",
      expectedTypeParameterNames = List("Left", "Right", "Result0"),
      expectedUpperBoundName = "Natural",
      expectedResultMemberName = "Result"
    )
  }

  test("uses the decoder-selected collision-free result parameter") {
    val definition = AuxDefinitionBuilder.definition(
      AuxSourceShapeDecoder.Shape(
        typeClassName = "Weird",
        firstTypeParameterName = "Out0",
        secondTypeParameterName = "M",
        upperBoundTypeName = "Nat",
        resultTypeMemberName = "Out",
        generatedResultParameterName = "Out1"
      )
    )

    assertEquals(
      definition.syntax,
      """type Aux[Out0 <: Nat, M <: Nat, Out1 <: Nat] = Weird[Out0, M] {
        |  type Out = Out1
        |}""".stripMargin
    )
    assertTypedTopology(
      definition,
      expectedTypeClassName = "Weird",
      expectedTypeParameterNames = List("Out0", "M", "Out1"),
      expectedUpperBoundName = "Nat",
      expectedResultMemberName = "Out"
    )
  }

  private def assertTypedTopology(
      definition: Defn.Type,
      expectedTypeClassName: String,
      expectedTypeParameterNames: List[String],
      expectedUpperBoundName: String,
      expectedResultMemberName: String
  ): Unit =
    assertEquals(definition.name.value, "Aux")
    assertEquals(
      definition.tparamClause.values.map(_.name.value),
      expectedTypeParameterNames
    )
    assertEquals(
      definition.tparamClause.values.map(_.tbounds.hi.map(_.syntax)),
      List.fill(3)(Some(expectedUpperBoundName))
    )

    definition.body match
      case refinement: Type.Refine =>
        refinement.tpe match
          case Some(target: Type.Apply) =>
            assertEquals(target.tpe.syntax, expectedTypeClassName)
            assertEquals(
              target.argClause.values.map(_.syntax),
              expectedTypeParameterNames.take(2)
            )
          case other => fail(s"expected applied target, found $other")
        refinement.stats match
          case List(memberEquality: Defn.Type) =>
            assertEquals(memberEquality.name.value, expectedResultMemberName)
            assertEquals(
              memberEquality.body.syntax,
              expectedTypeParameterNames.last
            )
          case other => fail(s"expected one type equality, found $other")
      case other => fail(s"expected refined alias body, found $other")
