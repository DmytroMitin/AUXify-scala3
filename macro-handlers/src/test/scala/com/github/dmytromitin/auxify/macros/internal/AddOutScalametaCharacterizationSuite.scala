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
      typeMemberNames = List("Out"),
      contextualParameterName = "inst"
    )

    assertEquals(characterized.definition.syntax, CanonicalSource)
    assertEquals(characterized.typeParameters.map(_.syntax), List("N <: Nat", "M <: Nat"))
    assertEquals(characterized.target.syntax, "Add[N, M]")
    assertEquals(characterized.selectedTypes.map(_.syntax), List("inst.Out"))
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
      typeMemberNames = List("Result"),
      contextualParameterName = "evidence"
    )

    assertEquals(
      characterized.definition.syntax,
      """def apply[Left <: Domain, Right <: Domain](using evidence: Combine[Left, Right]): Combine[Left, Right] {
        |  type Result = evidence.Result
        |} = evidence""".stripMargin
    )
  }

  test("two-member full apply preserves both ordered evidence selections") {
    val rows = List(
      (
        "BiApply",
        "N",
        "M",
        "Nat",
        List("Out", "Carry"),
        "inst",
        """def apply[N <: Nat, M <: Nat](using inst: BiApply[N, M]): BiApply[N, M] {
          |  type Out = inst.Out
          |  type Carry = inst.Carry
          |} = inst""".stripMargin
      ),
      (
        "BiEvidence",
        "Left",
        "Right",
        "Domain",
        List("Result", "Remainder"),
        "evidence",
        """def apply[Left <: Domain, Right <: Domain](using evidence: BiEvidence[Left, Right]): BiEvidence[Left, Right] {
          |  type Result = evidence.Result
          |  type Remainder = evidence.Remainder
          |} = evidence""".stripMargin
      )
    )

    rows.foreach:
      (className, first, second, bound, members, evidence, expectedSource) =>
        val characterized = characterize(
          className,
          first,
          second,
          bound,
          members,
          evidence
        )

        assertEquals(characterized.definition.syntax, expectedSource)
        assertEquals(
          characterized.typeParameters.map(_.name.value),
          List(first, second)
        )
        assertEquals(
          characterized.selectedTypes.map(_.syntax),
          members.map(member => s"$evidence.$member")
        )
        characterized.definition.paramClauseGroups match
          case List(group) =>
            assertEquals(group.tparamClause.values, characterized.typeParameters)
            group.paramClauses match
              case List(clause) =>
                assert(clause.mod.exists(_.isInstanceOf[Mod.Using]))
                assertEquals(clause.values.map(_.name.value), List(evidence))
                assertEquals(
                  clause.values.head.decltpe.map(_.syntax),
                  Some(characterized.target.syntax)
                )
              case other => fail(s"expected one contextual clause, found $other")
          case other => fail(s"expected one parameter-clause group, found $other")
        characterized.refinedType match
          case Type.Refine(Some(base), refinements) =>
            assertEquals(base.syntax, characterized.target.syntax)
            assertEquals(
              refinements.collect:
                case member: Defn.Type => member.name.value -> member.body.syntax,
              members.map(member => member -> s"$evidence.$member")
            )
          case other => fail(s"expected a two-member refined result, found $other")
        characterized.definition.body match
          case Term.Name(name) => assertEquals(name, evidence)
          case other => fail(s"expected body Term.Name($evidence), found $other")
  }

  private final case class Characterized(
      typeParameters: List[Type.Param],
      target: Type,
      selectedTypes: List[Type],
      refinedType: Type,
      definition: Defn.Def
  )

  private def characterize(
      className: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundName: String,
      typeMemberNames: List[String],
      contextualParameterName: String
  ): Characterized =
    val classNameTree = Type.Name(className)
    val firstTypeParameterNameTree = Type.Name(firstTypeParameterName)
    val secondTypeParameterNameTree = Type.Name(secondTypeParameterName)
    val upperBoundNameTree = Type.Name(upperBoundName)
    val typeMemberNameTrees = typeMemberNames.map(Type.Name(_))
    val contextualParameterNameTree = Term.Name(contextualParameterName)

    val firstTypeParameter: Type.Param =
      tparam"$firstTypeParameterNameTree <: $upperBoundNameTree"
    val secondTypeParameter: Type.Param =
      tparam"$secondTypeParameterNameTree <: $upperBoundNameTree"
    val typeParameters = List(firstTypeParameter, secondTypeParameter)
    val typeArguments =
      List(firstTypeParameterNameTree, secondTypeParameterNameTree)
    val target: Type = t"$classNameTree[..$typeArguments]"
    val selectedTypes: List[Type] =
      typeMemberNameTrees.map(member => t"$contextualParameterNameTree.$member")
    val equalities: List[Stat] =
      typeMemberNameTrees.zip(selectedTypes).map: (member, selected) =>
        q"type $member = $selected"
    val refinedType: Type = t"$target { ..$equalities }"
    val definition: Defn.Def =
      q"def apply[..$typeParameters](using $contextualParameterNameTree: $target): $refinedType = $contextualParameterNameTree"

    Characterized(
      typeParameters,
      target,
      selectedTypes,
      refinedType,
      definition
    )
