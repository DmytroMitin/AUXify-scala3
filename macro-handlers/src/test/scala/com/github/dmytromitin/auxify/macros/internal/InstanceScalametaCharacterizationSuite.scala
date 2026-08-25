package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class InstanceScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    """def instance[A](emptyValue: => A, combineFunction: (A, A) => A): Monoid[A] = new Monoid[A] {
      |  override def empty: A = emptyValue
      |  override def combine(a: A, a1: A): A = combineFunction(a, a1)
      |}""".stripMargin

  test("Scala 3 quasiquotes characterize the complete canonical instance factory") {
    val characterized = characterize(
      traitName = "Monoid",
      typeParameterName = "A",
      factoryName = "instance",
      valueMemberName = "empty",
      methodMemberName = "combine",
      valueParameterName = "emptyValue",
      functionParameterName = "combineFunction",
      firstMethodParameterName = "a",
      secondMethodParameterName = "a1"
    )

    assertEquals(characterized.definition.syntax, CanonicalSource)
    assertEquals(characterized.typeParameter.syntax, "A")
    assertEquals(characterized.target.syntax, "Monoid[A]")
    assertEquals(characterized.byNameType.syntax, "=> A")
    assertEquals(characterized.functionType.syntax, "(A, A) => A")
    assertEquals(
      characterized.factoryParameters.map(_.syntax),
      List("emptyValue: => A", "combineFunction: (A, A) => A")
    )
    assertEquals(characterized.definition.name.value, "instance")
    assertEquals(characterized.definition.decltpe.map(_.syntax), Some("Monoid[A]"))
    assertEquals(characterized.definition.body, characterized.implementation)
    assertEquals(
      characterized.overrides.map(_.syntax),
      List(
        "override def empty: A = emptyValue",
        "override def combine(a: A, a1: A): A = combineFunction(a, a1)"
      )
    )
    assert(characterized.overrides.forall(_.mods.exists(_.isInstanceOf[Mod.Override])))
  }

  test("characterization derives dynamic factory member and parameter names") {
    val characterized = characterize(
      traitName = "Choice",
      typeParameterName = "Element",
      factoryName = "make",
      valueMemberName = "fallback",
      methodMemberName = "select",
      valueParameterName = "fallbackValue",
      functionParameterName = "selection",
      firstMethodParameterName = "left",
      secondMethodParameterName = "right"
    )

    assertEquals(
      characterized.definition.syntax,
      """def make[Element](fallbackValue: => Element, selection: (Element, Element) => Element): Choice[Element] = new Choice[Element] {
        |  override def fallback: Element = fallbackValue
        |  override def select(left: Element, right: Element): Element = selection(left, right)
        |}""".stripMargin
    )
  }

  private final case class Characterized(
      typeParameter: Type.Param,
      target: Type,
      byNameType: Type,
      functionType: Type,
      factoryParameters: List[Term.Param],
      overrides: List[Defn.Def],
      implementation: Term.NewAnonymous,
      definition: Defn.Def
  )

  private def characterize(
      traitName: String,
      typeParameterName: String,
      factoryName: String,
      valueMemberName: String,
      methodMemberName: String,
      valueParameterName: String,
      functionParameterName: String,
      firstMethodParameterName: String,
      secondMethodParameterName: String
  ): Characterized =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val factoryNameTree = Term.Name(factoryName)
    val valueMemberNameTree = Term.Name(valueMemberName)
    val methodMemberNameTree = Term.Name(methodMemberName)
    val valueParameterNameTree = Term.Name(valueParameterName)
    val functionParameterNameTree = Term.Name(functionParameterName)
    val firstMethodParameterNameTree = Term.Name(firstMethodParameterName)
    val secondMethodParameterNameTree = Term.Name(secondMethodParameterName)

    val typeParameter: Type.Param = tparam"$typeParameterNameTree"
    val typeArguments: List[Type] = List(typeParameterNameTree)
    val target: Type = t"$traitNameTree[..$typeArguments]"
    val byNameType: Type = t"=> $typeParameterNameTree"
    val functionType: Type =
      t"($typeParameterNameTree, $typeParameterNameTree) => $typeParameterNameTree"
    val valueParameter: Term.Param =
      param"$valueParameterNameTree: $byNameType"
    val functionParameter: Term.Param =
      param"$functionParameterNameTree: $functionType"
    val factoryParameters = List(valueParameter, functionParameter)
    val firstMethodParameter: Term.Param =
      param"$firstMethodParameterNameTree: $typeParameterNameTree"
    val secondMethodParameter: Term.Param =
      param"$secondMethodParameterNameTree: $typeParameterNameTree"
    val valueOverride: Defn.Def =
      q"override def $valueMemberNameTree: $typeParameterNameTree = $valueParameterNameTree"
    val methodOverride: Defn.Def =
      q"override def $methodMemberNameTree($firstMethodParameter, $secondMethodParameter): $typeParameterNameTree = $functionParameterNameTree($firstMethodParameterNameTree, $secondMethodParameterNameTree)"
    val overrides = List(valueOverride, methodOverride)
    val overrideStats: List[Stat] = overrides
    val parent: Init =
      Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$overrideStats }"
    val typeParameters = List(typeParameter)
    val definition: Defn.Def =
      q"def $factoryNameTree[..$typeParameters](..$factoryParameters): $target = $implementation"

    Characterized(
      typeParameter,
      target,
      byNameType,
      functionType,
      factoryParameters,
      overrides,
      implementation,
      definition
    )
