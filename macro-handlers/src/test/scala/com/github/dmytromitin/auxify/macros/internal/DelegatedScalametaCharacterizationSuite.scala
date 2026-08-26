package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class DelegatedScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    "def show[A](a: A)(using inst: Show[A]): String = inst.show(a)"

  test("Scala 3 quasiquotes characterize the complete canonical delegated forwarder") {
    val characterized = characterize(
      traitName = "Show",
      typeParameterName = "A",
      methodName = "show",
      valueParameterName = "a",
      contextualParameterName = "inst",
      resultTypeName = "String"
    )

    assertEquals(characterized.definition.syntax, CanonicalSource)
    assertEquals(characterized.typeParameters.map(_.syntax), List("A"))
    assertEquals(characterized.target.syntax, "Show[A]")
    assertEquals(characterized.ordinaryParameter.syntax, "a: A")
    assertEquals(characterized.contextualParameter.syntax, "inst: Show[A]")
    assertEquals(characterized.resultType.syntax, "String")
    assertEquals(characterized.definition.name.value, "show")
    assertEquals(characterized.definition.decltpe.map(_.syntax), Some("String"))

    characterized.definition.paramClauseGroups match
      case group :: Nil =>
        assertEquals(group.tparamClause.values.map(_.syntax), List("A"))
        group.paramClauses match
          case ordinary :: contextual :: Nil =>
            assertEquals(ordinary.values.map(_.syntax), List("a: A"))
            assertEquals(contextual.values.map(_.syntax), List("inst: Show[A]"))
            assert(contextual.mod.exists(_.isInstanceOf[Mod.Using]))
          case other => fail(s"expected ordinary and final contextual clauses, found $other")
      case other => fail(s"expected one parameter-clause group, found $other")

    characterized.definition.body match
      case application: Term.Apply =>
        application.fun match
          case Term.Select(Term.Name(receiver), Term.Name(method)) =>
            assertEquals(receiver, "inst")
            assertEquals(method, "show")
          case other => fail(s"expected inst.show selection, found ${other.structure}")
        application.argClause.values match
          case Term.Name(argument) :: Nil => assertEquals(argument, "a")
          case other => fail(s"expected one a argument, found $other")
      case other => fail(s"expected inst.show(a) forwarding body, found ${other.structure}")
  }

  test("characterization derives trait method type value evidence and result names") {
    val characterized = characterize(
      traitName = "Render",
      typeParameterName = "Item",
      methodName = "render",
      valueParameterName = "value",
      contextualParameterName = "evidence",
      resultTypeName = "Text"
    )

    assertEquals(
      characterized.definition.syntax,
      "def render[Item](value: Item)(using evidence: Render[Item]): Text = evidence.render(value)"
    )
  }

  private final case class Characterized(
      typeParameters: List[Type.Param],
      target: Type,
      ordinaryParameter: Term.Param,
      contextualParameter: Term.Param,
      resultType: Type,
      invocation: Term,
      definition: Defn.Def
  )

  private def characterize(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      valueParameterName: String,
      contextualParameterName: String,
      resultTypeName: String
  ): Characterized =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val methodNameTree = Term.Name(methodName)
    val valueParameterNameTree = Term.Name(valueParameterName)
    val contextualParameterNameTree = Term.Name(contextualParameterName)
    val resultTypeNameTree = Type.Name(resultTypeName)

    val typeParameter: Type.Param = tparam"$typeParameterNameTree"
    val typeParameters = List(typeParameter)
    val typeArguments: List[Type] = List(typeParameterNameTree)
    val target: Type = t"$traitNameTree[..$typeArguments]"
    val ordinaryParameter: Term.Param =
      param"$valueParameterNameTree: $typeParameterNameTree"
    val contextualParameter: Term.Param =
      param"$contextualParameterNameTree: $target"
    val resultType: Type = t"$resultTypeNameTree"
    val invocation: Term =
      q"$contextualParameterNameTree.$methodNameTree($valueParameterNameTree)"
    val definition: Defn.Def =
      q"def $methodNameTree[..$typeParameters]($ordinaryParameter)(using $contextualParameterNameTree: $target): $resultType = $invocation"

    Characterized(
      typeParameters,
      target,
      ordinaryParameter,
      contextualParameter,
      resultType,
      invocation,
      definition
    )
