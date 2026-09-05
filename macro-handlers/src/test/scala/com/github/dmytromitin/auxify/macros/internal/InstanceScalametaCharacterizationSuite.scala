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

  test("Scala 3 quasiquotes characterize the strict one-abstract-val factory") {
    val characterized = characterizeAbstractVal(
      traitName = "HasValue",
      typeParameterName = "A",
      memberName = "value",
      factoryName = "instance",
      occupiedTermNames = Set("instance", "value")
    )

    assertEquals(
      characterized.definition.syntax,
      "def instance[A](valueValue: A): HasValue[A] = new HasValue[A] { override val value: A = valueValue }"
    )
    assertEquals(characterized.carrier.decltpe.map(_.syntax), Some("A"))
    assert(!characterized.carrier.decltpe.exists(_.isInstanceOf[Type.ByName]))
    assertEquals(characterized.implementation.templ.stats.map(_.syntax), List("override val value: A = valueValue"))
  }

  test("abstract-val characterization renames coherently and freshens a colliding readable carrier") {
    val characterized = characterizeAbstractVal(
      traitName = "Container",
      typeParameterName = "Element",
      memberName = "valueValue",
      factoryName = "make",
      occupiedTermNames = Set("make", "valueValue")
    )

    assertEquals(characterized.carrier.name.value, "valueValue1")
    assertEquals(
      characterized.definition.syntax,
      "def make[Element](valueValue1: Element): Container[Element] = new Container[Element] { override val valueValue: Element = valueValue1 }"
    )
  }

  test("Scala 3 quasiquotes characterize the one-abstract-type-member factory") {
    val characterized = characterizeAbstractType(
      traitName = "HasOut",
      enclosingTypeParameterName = "A",
      memberName = "Out",
      factoryName = "instance",
      occupiedTypeNames = Set("A", "Out")
    )

    assertEquals(characterized.generatedTypeParameter.name.value, "Out0")
    assertEquals(characterized.target.syntax, "HasOut[A]")
    assertEquals(
      characterized.refinedReturnType.syntax,
      """HasOut[A] {
        |  type Out = Out0
        |}""".stripMargin
    )
    assertEquals(characterized.returnEquality.syntax, "type Out = Out0")
    assertEquals(characterized.implementationAlias.syntax, "type Out = Out0")
    assertEquals(
      characterized.definition.syntax,
      """def instance[A, Out0]: HasOut[A] {
        |  type Out = Out0
        |} = new HasOut[A] { type Out = Out0 }""".stripMargin
    )
    assertEquals(
      characterized.implementation.templ.stats.map(_.syntax),
      List("type Out = Out0")
    )
  }

  test("abstract-type characterization renames coherently and freshens a colliding readable type parameter") {
    val characterized = characterizeAbstractType(
      traitName = "Container",
      enclosingTypeParameterName = "Element0",
      memberName = "Element",
      factoryName = "make",
      occupiedTypeNames = Set("Element0", "Element")
    )

    assertEquals(characterized.generatedTypeParameter.name.value, "Element1")
    assertEquals(
      characterized.definition.syntax,
      """def make[Element0, Element1]: Container[Element0] {
        |  type Element = Element1
        |} = new Container[Element0] { type Element = Element1 }""".stripMargin
    )
  }

  test("Scala 3 quasiquotes characterize the strict curried-method instance factory") {
    val characterized = characterizeCurriedMethod(
      traitName = "Curried",
      typeParameterName = "A",
      methodName = "combine",
      firstParameterName = "a",
      secondParameterName = "b",
      factoryName = "instance",
      occupiedTermNames = Set("instance", "combine", "a", "b")
    )

    assertEquals(
      characterized.definition.syntax,
      "def instance[A](combineFunction: A => A => A): Curried[A] = new Curried[A] { override def combine(a: A)(b: A): A = combineFunction(a)(b) }"
    )
    characterized.carrier.decltpe match
      case Some(Type.Function(List(Type.Name("A")), Type.Function(List(Type.Name("A")), Type.Name("A")))) => ()
      case other => fail(s"expected nested unary function carrier, found $other")
    characterized.definition.paramClauseGroups match
      case List(group) =>
        assertEquals(group.tparamClause.values.map(_.name.value), List("A"))
        assertEquals(group.paramClauses.map(_.values.map(_.name.value)), List(List("combineFunction")))
        assert(group.paramClauses.forall(_.mod.isEmpty))
      case other => fail(s"expected one factory clause group, found $other")
    assertEquals(characterized.target.syntax, "Curried[A]")
    assertEquals(characterized.implementation.templ.inits.map(_.tpe.syntax), List("Curried[A]"))
    assertEquals(characterized.implementation.templ.stats, List(characterized.overrideMember))
    assert(characterized.overrideMember.paramClauseGroups.flatMap(_.paramClauses).forall(_.mod.isEmpty))
    assertEquals(
      characterized.overrideMember.paramClauseGroups.flatMap(_.paramClauses).map(_.values.map(_.syntax)),
      List(List("a: A"), List("b: A"))
    )
    characterized.overrideMember.body match
      case Term.Apply(Term.Apply(Term.Name("combineFunction"), List(Term.Name("a"))), List(Term.Name("b"))) => ()
      case other => fail(s"expected two successive carrier applications, found ${other.syntax}")
  }

  test("curried-method characterization renames coherently and freshens a colliding readable carrier") {
    val characterized = characterizeCurriedMethod(
      traitName = "Chain",
      typeParameterName = "Element",
      methodName = "append",
      firstParameterName = "left",
      secondParameterName = "right",
      factoryName = "make",
      occupiedTermNames = Set("make", "append", "combineFunction", "left", "right")
    )

    assertEquals(characterized.carrier.name.value, "combineFunction1")
    assertEquals(
      characterized.definition.syntax,
      "def make[Element](combineFunction1: Element => Element => Element): Chain[Element] = new Chain[Element] { override def append(left: Element)(right: Element): Element = combineFunction1(left)(right) }"
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

  private final case class CharacterizedAbstractVal(
      carrier: Term.Param,
      implementation: Term.NewAnonymous,
      definition: Defn.Def
  )

  private final case class CharacterizedAbstractType(
      generatedTypeParameter: Type.Param,
      target: Type,
      refinedReturnType: Type,
      returnEquality: Defn.Type,
      implementationAlias: Defn.Type,
      implementation: Term.NewAnonymous,
      definition: Defn.Def
  )

  private final case class CharacterizedCurriedMethod(
      carrier: Term.Param,
      target: Type,
      overrideMember: Defn.Def,
      implementation: Term.NewAnonymous,
      definition: Defn.Def
  )

  private def characterizeCurriedMethod(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      firstParameterName: String,
      secondParameterName: String,
      factoryName: String,
      occupiedTermNames: Set[String]
  ): CharacterizedCurriedMethod =
    val targetName = Type.Name(traitName)
    val typeName = Type.Name(typeParameterName)
    val memberName = Term.Name(methodName)
    val firstName = Term.Name(firstParameterName)
    val secondName = Term.Name(secondParameterName)
    val factory = Term.Name(factoryName)
    val carrierName = Term.Name(
      freshCarrierName("combineFunction", occupiedTermNames)
    )
    val typeParameter: Type.Param = tparam"$typeName"
    val target: Type = t"$targetName[$typeName]"
    val nestedFunctionType: Type =
      Type.Function(
        List(typeName),
        Type.Function(List(typeName), typeName)
      )
    val carrier: Term.Param = param"$carrierName: $nestedFunctionType"
    val firstParameter: Term.Param = param"$firstName: $typeName"
    val secondParameter: Term.Param = param"$secondName: $typeName"
    val nestedApplication: Term =
      Term.Apply(
        Term.Apply(carrierName, List(firstName)),
        List(secondName)
      )
    val overrideMember: Defn.Def =
      q"override def $memberName($firstParameter)($secondParameter): $typeName = $nestedApplication"
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementationStats: List[Stat] = List(overrideMember)
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$implementationStats }"
    val definition: Defn.Def =
      q"def $factory[$typeParameter]($carrier): $target = $implementation"

    CharacterizedCurriedMethod(
      carrier,
      target,
      overrideMember,
      implementation,
      definition
    )

  private def characterizeAbstractType(
      traitName: String,
      enclosingTypeParameterName: String,
      memberName: String,
      factoryName: String,
      occupiedTypeNames: Set[String]
  ): CharacterizedAbstractType =
    val targetName = Type.Name(traitName)
    val enclosingTypeName = Type.Name(enclosingTypeParameterName)
    val memberTypeName = Type.Name(memberName)
    val generatedTypeName = Type.Name(
      freshTypeParameterName(memberName, occupiedTypeNames)
    )
    val factory = Term.Name(factoryName)
    val enclosingTypeParameter: Type.Param = tparam"$enclosingTypeName"
    val generatedTypeParameter: Type.Param = tparam"$generatedTypeName"
    val target: Type = t"$targetName[$enclosingTypeName]"
    val returnEquality: Defn.Type =
      q"type $memberTypeName = $generatedTypeName"
    val refinedReturnType: Type =
      t"$target { type $memberTypeName = $generatedTypeName }"
    val implementationAlias: Defn.Type =
      q"type $memberTypeName = $generatedTypeName"
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementationStats: List[Stat] = List(implementationAlias)
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$implementationStats }"
    val typeParameters = List(enclosingTypeParameter, generatedTypeParameter)
    val definition: Defn.Def =
      q"def $factory[..$typeParameters]: $refinedReturnType = $implementation"

    CharacterizedAbstractType(
      generatedTypeParameter,
      target,
      refinedReturnType,
      returnEquality,
      implementationAlias,
      implementation,
      definition
    )

  private def freshTypeParameterName(
      memberName: String,
      occupied: Set[String]
  ): String =
    (0 to occupied.size)
      .iterator
      .map(index => s"$memberName$index")
      .find(name => !occupied.contains(name))
      .getOrElse(s"${memberName}0")

  private def characterizeAbstractVal(
      traitName: String,
      typeParameterName: String,
      memberName: String,
      factoryName: String,
      occupiedTermNames: Set[String]
  ): CharacterizedAbstractVal =
    val targetName = Type.Name(traitName)
    val typeName = Type.Name(typeParameterName)
    val memberPattern = Pat.Var(Term.Name(memberName))
    val factory = Term.Name(factoryName)
    val carrierName = Term.Name(
      freshCarrierName("valueValue", occupiedTermNames)
    )
    val typeParameter: Type.Param = tparam"$typeName"
    val target: Type = t"$targetName[$typeName]"
    val carrier: Term.Param = param"$carrierName: $typeName"
    val implementationMember: Defn.Val =
      q"override val $memberPattern: $typeName = $carrierName"
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementationStats: List[Stat] = List(implementationMember)
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$implementationStats }"
    val typeParameters = List(typeParameter)
    val parameters = List(carrier)
    val definition: Defn.Def =
      q"def $factory[..$typeParameters](..$parameters): $target = $implementation"

    CharacterizedAbstractVal(carrier, implementation, definition)

  private def freshCarrierName(
      stem: String,
      occupied: Set[String]
  ): String =
    (0 to occupied.size)
      .iterator
      .map(index => if index == 0 then stem else s"$stem$index")
      .find(name => !occupied.contains(name))
      .getOrElse(stem)

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
