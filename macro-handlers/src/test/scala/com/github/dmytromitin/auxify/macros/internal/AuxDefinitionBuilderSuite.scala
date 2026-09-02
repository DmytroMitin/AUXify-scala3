package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}
import dotty.tools.dotc.core.Flags.Param
import dotty.tools.dotc.core.Symbols.NoSymbol

import scala.annotation.nowarn
import scala.meta.*

@nowarn("cat=deprecation")
class AuxDefinitionBuilderSuite extends munit.FunSuite:
  test("lowers the canonical typed definition through the positioned C004 bridge") {
    withContext:
      val lowered = lower(
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
        lowered.generatedSource,
        "type Aux[N <: Nat, M <: Nat, Out0 <: Nat] = Add[N, M] { type Out = Out0 }"
      )
      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedAddAux.scala")
      assertLoweredAlias(
        lowered.tree,
        expectedTypeClassName = "Add",
        expectedTypeParameterNames = List("N", "M", "Out0"),
        expectedUpperBoundName = "Nat",
        expectedResultMemberName = "Out"
      )
      assertGeneratedOrigin(lowered.tree, lowered.generatedSource, lowered.virtualSourceName)
  }

  test("lowers renamed and collision-safe source-derived names") {
    withContext:
      val lowered = lower(
        AuxSourceShapeDecoder.Shape(
          typeClassName = "Combine",
          firstTypeParameterName = "Result0",
          secondTypeParameterName = "Right",
          upperBoundTypeName = "Natural",
          resultTypeMemberName = "Result",
          generatedResultParameterName = "Result1"
        )
      )

      assertEquals(
        lowered.generatedSource,
        "type Aux[Result0 <: Natural, Right <: Natural, Result1 <: Natural] = Combine[Result0, Right] { type Result = Result1 }"
      )
      assertEquals(lowered.virtualSourceName, "AuxifyGeneratedCombineAux.scala")
      assertLoweredAlias(
        lowered.tree,
        expectedTypeClassName = "Combine",
        expectedTypeParameterNames = List("Result0", "Right", "Result1"),
        expectedUpperBoundName = "Natural",
        expectedResultMemberName = "Result"
      )
  }

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

  private def lower(
      shape: AuxSourceShapeDecoder.Shape
  )(using Context) =
    AuxDefinitionBuilder
      .lower(shape)
      .fold(problem => fail(s"${problem.code}: ${problem.detail}"), identity)

  private def assertLoweredAlias(
      tree: untpd.TypeDef,
      expectedTypeClassName: String,
      expectedTypeParameterNames: List[String],
      expectedUpperBoundName: String,
      expectedResultMemberName: String
  )(using Context): Unit =
    assertEquals(tree.name.toString, "Aux")
    tree.rhs match
      case untpd.LambdaTypeTree(parameters, untpd.RefinedTypeTree(target, List(result: untpd.TypeDef))) =>
        assertEquals(parameters.map(_.name.toString), expectedTypeParameterNames)
        parameters.foreach: parameter =>
          assertEquals(parameter.mods.flags, Param)
          parameter.rhs match
            case untpd.TypeBoundsTree(lower, untpd.Ident(upper), alias) =>
              assert(lower.isEmpty)
              assertEquals(upper.toString, expectedUpperBoundName)
              assert(alias.isEmpty)
            case other => fail(s"expected upper-only TypeBoundsTree, found $other")
        target match
          case untpd.AppliedTypeTree(untpd.Ident(name), arguments) =>
            assertEquals(name.toString, expectedTypeClassName)
            assertEquals(
              arguments.collect { case untpd.Ident(argument) => argument.toString },
              expectedTypeParameterNames.take(2)
            )
          case other => fail(s"expected applied type-class target, found $other")
        assertEquals(result.name.toString, expectedResultMemberName)
        result.rhs match
          case untpd.Ident(name) =>
            assertEquals(name.toString, expectedTypeParameterNames.last)
          case other => fail(s"expected direct result-parameter reference, found $other")
      case other => fail(s"expected lowered Aux alias topology, found $other")

  private def assertGeneratedOrigin(
      tree: untpd.Tree,
      generatedSource: String,
      virtualSourceName: String
  )(using Context): Unit =
    val trees = allTrees(tree)
    assertEquals(trees.size, 18)
    trees.foreach: value =>
      assert(value.source.exists, clue(value))
      assertEquals(value.source.path, virtualSourceName, clue(value))
      assertEquals(value.source.content.mkString, generatedSource, clue(value))
      assert(value.span.exists, clue(value))
      assert(value.span.start >= 0, clue(value))
      assert(value.span.end <= generatedSource.length, clue(value))
      assertEquals(value.symbol, NoSymbol, clue(value))
      assert(!value.isInstanceOf[untpd.TypedSplice], clue(value))

  private def allTrees(tree: untpd.Tree)(using Context): Vector[untpd.Tree] =
    if tree.isEmpty then Vector.empty
    else tree +: directChildren(tree).flatMap(allTrees)

  private def directChildren(tree: untpd.Tree)(using Context): Vector[untpd.Tree] =
    tree match
      case value: untpd.TypeDef => Vector(value.rhs)
      case value: untpd.LambdaTypeTree => value.tparams.toVector :+ value.body
      case value: untpd.TypeBoundsTree =>
        Vector(value.lo, value.hi, value.alias).filterNot(_.isEmpty)
      case value: untpd.RefinedTypeTree => value.tpt +: value.refinements.toVector
      case value: untpd.AppliedTypeTree => value.tpt +: value.args.toVector
      case _ => Vector.empty

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
