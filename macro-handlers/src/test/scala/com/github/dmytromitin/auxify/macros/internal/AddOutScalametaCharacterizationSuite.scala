package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class AddOutScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    """def apply[N <: Nat, M <: Nat](using inst: Add[N, M]): Add[N, M] {
      |  type Out = inst.Out
      |} = inst""".stripMargin

  test("Scala 3 quasiquotes characterize the complete Add.Out materializer") {
    val characterized = characterize(
      className = "Add",
      firstTypeParameterName = "N",
      secondTypeParameterName = "M",
      upperBoundName = "Nat",
      typeMemberName = "Out",
      contextualParameterName = "inst"
    )

    assertEquals(characterized.definition.syntax, CanonicalSource)
    assertEquals(characterized.typeParameters.map(_.syntax), List("N <: Nat", "M <: Nat"))
    assertEquals(characterized.target.syntax, "Add[N, M]")
    assertEquals(characterized.selectedType.syntax, "inst.Out")
    assertEquals(
      characterized.refinedType.syntax,
      """Add[N, M] {
        |  type Out = inst.Out
        |}""".stripMargin
    )

    characterized.definition.body match
      case Term.Name(value) => assertEquals(value, "inst")
      case other => fail(s"expected stable identifier body, found $other")
  }

  test("characterization derives class parameter bound member and evidence names") {
    val characterized = characterize(
      className = "Combine",
      firstTypeParameterName = "Left",
      secondTypeParameterName = "Right",
      upperBoundName = "Domain",
      typeMemberName = "Result",
      contextualParameterName = "evidence"
    )

    assertEquals(
      characterized.definition.syntax,
      """def apply[Left <: Domain, Right <: Domain](using evidence: Combine[Left, Right]): Combine[Left, Right] {
        |  type Result = evidence.Result
        |} = evidence""".stripMargin
    )
  }

  private final case class Characterized(
      typeParameters: List[Type.Param],
      target: Type,
      selectedType: Type,
      refinedType: Type,
      definition: Defn.Def
  )

  private def characterize(
      className: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundName: String,
      typeMemberName: String,
      contextualParameterName: String
  ): Characterized =
    val classNameTree = Type.Name(className)
    val firstTypeParameterNameTree = Type.Name(firstTypeParameterName)
    val secondTypeParameterNameTree = Type.Name(secondTypeParameterName)
    val upperBoundNameTree = Type.Name(upperBoundName)
    val typeMemberNameTree = Type.Name(typeMemberName)
    val contextualParameterNameTree = Term.Name(contextualParameterName)

    val firstTypeParameter: Type.Param =
      tparam"$firstTypeParameterNameTree <: $upperBoundNameTree"
    val secondTypeParameter: Type.Param =
      tparam"$secondTypeParameterNameTree <: $upperBoundNameTree"
    val typeParameters = List(firstTypeParameter, secondTypeParameter)
    val typeArguments =
      List(firstTypeParameterNameTree, secondTypeParameterNameTree)
    val target: Type = t"$classNameTree[..$typeArguments]"
    val selectedType: Type =
      t"$contextualParameterNameTree.$typeMemberNameTree"
    val refinedType: Type =
      t"$target { type $typeMemberNameTree = $selectedType }"
    val definition: Defn.Def =
      q"def apply[..$typeParameters](using $contextualParameterNameTree: $target): $refinedType = $contextualParameterNameTree"

    Characterized(
      typeParameters,
      target,
      selectedType,
      refinedType,
      definition
    )
