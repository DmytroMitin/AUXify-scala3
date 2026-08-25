package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class AuxScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    """type Aux[N <: Nat, M <: Nat, Out0 <: Nat] = Add[N, M] {
      |  type Out = Out0
      |}""".stripMargin

  test("Scala 3 quasiquotes characterize the complete canonical Aux alias") {
    val traitName = Type.Name("Add")
    val aliasName = Type.Name("Aux")
    val firstName = Type.Name("N")
    val secondName = Type.Name("M")
    val memberName = Type.Name("Out")
    val addedName = Type.Name("Out0")
    val upperBound = Type.Name("Nat")

    val firstParameter: Type.Param = tparam"$firstName <: $upperBound"
    val secondParameter: Type.Param = tparam"$secondName <: $upperBound"
    val addedParameter: Type.Param = tparam"$addedName <: $upperBound"
    val originalParameters = List(firstParameter, secondParameter)
    val allParameters = originalParameters :+ addedParameter
    val originalArguments: List[Type] = List(firstName, secondName)
    val target: Type = t"$traitName[..$originalArguments]"
    val memberEquality: Defn.Type = q"type $memberName = $addedName"
    val memberEqualities: List[Stat] = List(memberEquality)
    val refinement: Type = t"$target { ..$memberEqualities }"
    val alias: Defn.Type =
      q"type $aliasName[..$allParameters] = $refinement"

    assertEquals(alias.syntax, CanonicalSource)
    assertEquals(originalParameters.map(_.syntax), List("N <: Nat", "M <: Nat"))
    assertEquals(addedParameter.syntax, "Out0 <: Nat")
    assertEquals(target.syntax, "Add[N, M]")
    assertEquals(memberEquality.syntax, "type Out = Out0")
    assertEquals(
      refinement.syntax,
      """Add[N, M] {
        |  type Out = Out0
        |}""".stripMargin
    )
    assertEquals(alias.name.value, "Aux")
    assertEquals(alias.tparamClause.values.map(_.syntax), List("N <: Nat", "M <: Nat", "Out0 <: Nat"))
    assertEquals(alias.body.syntax, refinement.syntax)
  }

  test("characterization derives dynamic names bounds and multiple member equalities") {
    val traitName = Type.Name("Relation")
    val aliasName = Type.Name("Evidence")
    val inputName = Type.Name("Input")
    val resultMemberName = Type.Name("Result")
    val errorMemberName = Type.Name("Failure")
    val resultParameterName = Type.Name("ResultValue")
    val errorParameterName = Type.Name("FailureValue")
    val resultUpperBound = Type.Name("Domain")
    val errorLowerBound = Type.Name("Bottom")
    val errorUpperBound = Type.Name("Problem")

    val inputParameter: Type.Param = tparam"$inputName"
    val resultParameter: Type.Param =
      tparam"$resultParameterName <: $resultUpperBound"
    val errorParameter: Type.Param =
      tparam"$errorParameterName >: $errorLowerBound <: $errorUpperBound"
    val allParameters = List(inputParameter, resultParameter, errorParameter)
    val inputArguments: List[Type] = List(inputName)
    val target: Type = t"$traitName[..$inputArguments]"
    val resultEquality: Defn.Type =
      q"type $resultMemberName = $resultParameterName"
    val errorEquality: Defn.Type =
      q"type $errorMemberName = $errorParameterName"
    val memberEqualities: List[Stat] = List(resultEquality, errorEquality)
    val refinement: Type = t"$target { ..$memberEqualities }"
    val alias: Defn.Type =
      q"type $aliasName[..$allParameters] = $refinement"

    assertEquals(
      alias.syntax,
      """type Evidence[Input, ResultValue <: Domain, FailureValue >: Bottom <: Problem] = Relation[Input] {
        |  type Result = ResultValue
        |  type Failure = FailureValue
        |}""".stripMargin
    )
    assertEquals(
      alias.tparamClause.values.map(_.syntax),
      List("Input", "ResultValue <: Domain", "FailureValue >: Bottom <: Problem")
    )
    assertEquals(target.syntax, "Relation[Input]")
    assertEquals(
      memberEqualities.map(_.syntax),
      List("type Result = ResultValue", "type Failure = FailureValue")
    )
    assertEquals(alias.body.syntax, refinement.syntax)
  }
