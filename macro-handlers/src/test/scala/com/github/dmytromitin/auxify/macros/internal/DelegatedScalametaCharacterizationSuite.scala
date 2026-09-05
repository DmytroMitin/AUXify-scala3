package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class DelegatedScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    "def show[A](a: A)(using inst: Show[A]): String = inst.show(a)"

  test("Scala 3 quasiquotes preserve parameterless forwarding as a stable select") {
    val characterized = characterizeParameterless(
      traitName = "Empty",
      typeParameterName = "A",
      methodName = "empty",
      contextualParameterName = "inst"
    )

    assertEquals(
      characterized.definition.syntax,
      "def empty[A](using inst: Empty[A]): A = inst.empty"
    )
    assertEquals(characterized.typeParameter.syntax, "A")
    assertEquals(characterized.target.syntax, "Empty[A]")
    assertEquals(characterized.contextualParameter.syntax, "inst: Empty[A]")
    assertEquals(characterized.resultType.syntax, "A")

    characterized.definition.paramClauseGroups match
      case group :: Nil =>
        assertEquals(group.tparamClause.values.map(_.syntax), List("A"))
        group.paramClauses match
          case contextual :: Nil =>
            assertEquals(contextual.values.map(_.syntax), List("inst: Empty[A]"))
            assert(contextual.mod.exists(_.isInstanceOf[Mod.Using]))
          case other => fail(s"expected only one contextual clause, found $other")
      case other => fail(s"expected one parameter-clause group, found $other")

    characterized.definition.body match
      case Term.Select(Term.Name(receiver), Term.Name(selected)) =>
        assertEquals(receiver, "inst")
        assertEquals(selected, "empty")
      case other => fail(s"expected stable inst.empty selection, found ${other.structure}")
  }

  test("parameterless characterization keeps renamed collision-safe evidence names dynamic") {
    val characterized = characterizeParameterless(
      traitName = "EmptyLike",
      typeParameterName = "Value",
      methodName = "inst",
      contextualParameterName = "inst1"
    )

    assertEquals(
      characterized.definition.syntax,
      "def inst[Value](using inst1: EmptyLike[Value]): Value = inst1.inst"
    )
  }

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

  test("binary characterization preserves both ordered arguments and collision-safe evidence") {
    val rows = List(
      (
        "Eq",
        "A",
        "eqv",
        List("left", "right"),
        "Boolean",
        "inst",
        "def eqv[A](left: A, right: A)(using inst: Eq[A]): Boolean = inst.eqv(left, right)"
      ),
      (
        "Comparable",
        "Element",
        "matches",
        List("inst", "candidate"),
        "Decision",
        "inst1",
        "def matches[Element](inst: Element, candidate: Element)(using inst1: Comparable[Element]): Decision = inst1.matches(inst, candidate)"
      )
    )

    rows.foreach:
      (traitName, typeName, methodName, parameterNames, resultName, expectedEvidence, expectedSource) =>
        val characterized = characterizeBinary(
          traitName,
          typeName,
          methodName,
          parameterNames,
          resultName
        )

        assertEquals(characterized.evidenceName.value, expectedEvidence)
        assertEquals(characterized.definition.syntax, expectedSource)
        characterized.definition.paramClauseGroups match
          case List(group) =>
            assertEquals(group.tparamClause.values.map(_.name.value), List(typeName))
            group.paramClauses match
              case List(ordinary, contextual) =>
                assertEquals(ordinary.values.map(_.name.value), parameterNames)
                assertEquals(
                  ordinary.values.flatMap(_.decltpe).map(_.syntax),
                  List.fill(2)(typeName)
                )
                assert(ordinary.mod.isEmpty)
                assert(contextual.mod.exists(_.isInstanceOf[Mod.Using]))
                assertEquals(contextual.values.map(_.name.value), List(expectedEvidence))
                assertEquals(
                  contextual.values.head.decltpe.map(_.syntax),
                  Some(s"$traitName[$typeName]")
                )
              case other => fail(s"expected one ordinary and one using clause, found $other")
          case other => fail(s"expected one parameter-clause group, found $other")
        characterized.definition.decltpe match
          case Some(Type.Name(name)) => assertEquals(name, resultName)
          case other => fail(s"expected simple named result $resultName, found $other")
        characterized.definition.body match
          case Term.Apply(
                Term.Select(Term.Name(receiver), Term.Name(selected)),
                arguments
              ) =>
            assertEquals(receiver, expectedEvidence)
            assertEquals(selected, methodName)
            assertEquals(
              arguments.collect { case Term.Name(name) => name },
              parameterNames
            )
          case other => fail(s"expected selected binary application, found ${other.structure}")
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

  private final case class ParameterlessCharacterized(
      typeParameter: Type.Param,
      target: Type,
      contextualParameter: Term.Param,
      resultType: Type,
      body: Term,
      definition: Defn.Def
  )

  private final case class BinaryCharacterized(
      evidenceName: Term.Name,
      definition: Defn.Def
  )

  private def characterizeParameterless(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      contextualParameterName: String
  ): ParameterlessCharacterized =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val methodNameTree = Term.Name(methodName)
    val contextualParameterNameTree = Term.Name(contextualParameterName)

    val typeParameter: Type.Param = tparam"$typeParameterNameTree"
    val typeParameters: List[Type.Param] = List(typeParameter)
    val typeArguments: List[Type] = List(typeParameterNameTree)
    val target: Type = t"$traitNameTree[..$typeArguments]"
    val contextualParameter: Term.Param =
      param"$contextualParameterNameTree: $target"
    val resultType: Type = t"$typeParameterNameTree"
    val body: Term = q"$contextualParameterNameTree.$methodNameTree"
    val definition: Defn.Def =
      q"def $methodNameTree[..$typeParameters](using $contextualParameterNameTree: $target): $resultType = $body"

    ParameterlessCharacterized(
      typeParameter,
      target,
      contextualParameter,
      resultType,
      body,
      definition
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

  private def characterizeBinary(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      parameterNames: List[String],
      resultTypeName: String
  ): BinaryCharacterized =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val methodNameTree = Term.Name(methodName)
    val parameterNameTrees = parameterNames.map(Term.Name(_))
    val resultTypeNameTree = Type.Name(resultTypeName)
    val evidenceName = Term.Name(freshEvidenceName(parameterNames.toSet))
    val typeParameter: Type.Param = tparam"$typeParameterNameTree"
    val typeParameters: List[Type.Param] = List(typeParameter)
    val target: Type = t"$traitNameTree[$typeParameterNameTree]"
    val ordinaryParameters: List[Term.Param] =
      parameterNameTrees.map(name => param"$name: $typeParameterNameTree")
    val invocationArguments: List[Term] = parameterNameTrees
    val invocation: Term = q"$evidenceName.$methodNameTree(..$invocationArguments)"
    val definition: Defn.Def =
      q"def $methodNameTree[..$typeParameters](..$ordinaryParameters)(using $evidenceName: $target): $resultTypeNameTree = $invocation"

    BinaryCharacterized(evidenceName, definition)

  private def freshEvidenceName(occupied: Set[String]): String =
    (0 to occupied.size)
      .iterator
      .map(index => if index == 0 then "inst" else s"inst$index")
      .find(name => !occupied.contains(name))
      .getOrElse("inst")
