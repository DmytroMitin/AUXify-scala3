package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
class SelfScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalSource =
    """trait Nat { self =>
      |  type Self >: self.type <: Nat {
      |    type Self = self.Self
      |  }
      |  type ++ = Succ[Self]
      |}""".stripMargin

  test("canonical defaults produce a trait with a bounded abstract Self member") {
    val bodyMemberName = Type.Name("++")
    val successorName = Type.Name("Succ")
    val selfMemberName = Type.Name("Self")
    val successor: Type = t"$successorName[$selfMemberName]"
    val originalBodyMember: Defn.Type =
      q"type $bodyMemberName = $successor"

    val result = characterize(
      traitName = "Nat",
      typeParameterNames = Nil,
      selfMemberName = "Self",
      inputSelf = Self(Name.Anonymous(), None),
      originalBody = List(originalBodyMember),
      lowerBound = true,
      fBound = true
    )

    assertEquals(result.traitDefinition.syntax, CanonicalSource)
    assertEquals(result.traitDefinition.name.value, "Nat")
    assertEquals(result.outputSelf.name.value, "self")
    assertEquals(result.generatedMember.name.value, "Self")
    assertEquals(result.generatedMember.bounds.lo, result.lowerBound)
    assertEquals(result.generatedMember.bounds.hi, Some(result.upperBound))
    assert(result.lowerBound.exists(_.isInstanceOf[Type.Singleton]))
    assert(result.upperBound.isInstanceOf[Type.Refine])
    assert(result.selectedSelfMember.isInstanceOf[Type.Select])
    assertEquals(result.originalBody, List(originalBodyMember))
  }

  test("dynamic generic trait self and member names are derived rather than fixed") {
    val valueMemberName = Type.Name("Value")
    val parameterName = Type.Name("Element")
    val originalBodyMember: Defn.Type =
      q"type $valueMemberName = $parameterName"

    val result = characterize(
      traitName = "Node",
      typeParameterNames = List("Element"),
      selfMemberName = "ThisNode",
      inputSelf = Self(Term.Name("node"), None),
      originalBody = List(originalBodyMember),
      lowerBound = true,
      fBound = true
    )

    assertEquals(
      result.traitDefinition.syntax,
      """trait Node[Element] { node =>
        |  type ThisNode >: node.type <: Node[Element] {
        |    type ThisNode = node.ThisNode
        |  }
        |  type Value = Element
        |}""".stripMargin
    )
    assertEquals(result.target.syntax, "Node[Element]")
    assertEquals(result.selectedSelfMember.syntax, "node.ThisNode")
    assertEquals(result.fBoundEquality.map(_.syntax), Some("type ThisNode = node.ThisNode"))
  }

  test("three non-default option rows differ from the renamed default on only the selected bounds") {
    def optionRow(lowerBound: Boolean, fBound: Boolean): Characterization =
      characterize(
        traitName = "Domain",
        typeParameterNames = Nil,
        selfMemberName = "Element",
        inputSelf = Self(Term.Name("owner$2"), None),
        originalBody = Nil,
        lowerBound = lowerBound,
        fBound = fBound
      )

    val default = optionRow(lowerBound = true, fBound = true)
    val lowerOnly = optionRow(lowerBound = true, fBound = false)
    val fBoundOnly = optionRow(lowerBound = false, fBound = true)
    val neither = optionRow(lowerBound = false, fBound = false)

    assertEquals(
      default.generatedMember.syntax,
      "type Element >: owner$2.type <: Domain {\n  type Element = owner$2.Element\n}"
    )
    assertEquals(
      lowerOnly.generatedMember.syntax,
      "type Element >: owner$2.type <: Domain"
    )
    assertEquals(
      fBoundOnly.generatedMember.syntax,
      "type Element <: Domain {\n  type Element = owner$2.Element\n}"
    )
    assertEquals(neither.generatedMember.syntax, "type Element <: Domain")

    assertEquals(lowerOnly.lowerBound.map(_.syntax), default.lowerBound.map(_.syntax))
    assertEquals(lowerOnly.fBoundEquality, None)
    assertEquals(fBoundOnly.lowerBound, None)
    assertEquals(
      fBoundOnly.fBoundEquality.map(_.syntax),
      default.fBoundEquality.map(_.syntax)
    )
    assertEquals(neither.lowerBound, None)
    assertEquals(neither.fBoundEquality, None)
    assertEquals(
      List(default, lowerOnly, fBoundOnly, neither).map(_.target.syntax).distinct,
      List("Domain")
    )
    assertEquals(
      List(default, lowerOnly, fBoundOnly, neither).map(_.outputSelf.name.value).distinct,
      List("owner$2")
    )
  }

  test("a named self alias is preserved while an anonymous self obtains neutral intent") {
    val anonymous = characterize(
      traitName = "Nat",
      typeParameterNames = Nil,
      selfMemberName = "Self",
      inputSelf = Self(Name.Anonymous(), None),
      originalBody = Nil,
      lowerBound = true,
      fBound = true
    )
    val namedInput = Self(Term.Name("existing"), None)
    val named = characterize(
      traitName = "Nat",
      typeParameterNames = Nil,
      selfMemberName = "Self",
      inputSelf = namedInput,
      originalBody = Nil,
      lowerBound = true,
      fBound = true
    )

    assert(anonymous.inputSelf.name.isInstanceOf[Name.Anonymous])
    assertEquals(anonymous.outputSelf.name.value, "self")
    assertEquals(named.outputSelf.name.value, "existing")
    assertEquals(named.lowerBound.map(_.syntax), Some("existing.type"))
    assertEquals(named.selectedSelfMember.syntax, "existing.Self")
  }

  test("the generated member is prepended without rebuilding or reordering the original body") {
    val firstName = Term.Name("first")
    val secondName = Term.Name("second")
    val intType = Type.Name("Int")
    val first: Defn.Def = q"def $firstName: $intType = 1"
    val second: Defn.Def = q"def $secondName: $intType = 2"
    val originalBody: List[Stat] = List(first, second)

    val result = characterize(
      traitName = "Ordered",
      typeParameterNames = Nil,
      selfMemberName = "Self",
      inputSelf = Self(Name.Anonymous(), None),
      originalBody = originalBody,
      lowerBound = true,
      fBound = true
    )

    val outputBody = result.traitDefinition.templ.stats
    assert(outputBody.head.eq(result.generatedMember))
    assert(outputBody.tail.zip(originalBody).forall((actual, original) => actual.eq(original)))
    assertEquals(outputBody.map(_.syntax), result.generatedMember.syntax :: List("def first: Int = 1", "def second: Int = 2"))
  }

  private final case class Characterization(
      inputSelf: Self,
      outputSelf: Self,
      target: Type,
      lowerBound: Option[Type],
      selectedSelfMember: Type,
      fBoundEquality: Option[Defn.Type],
      upperBound: Type,
      generatedMember: Decl.Type,
      originalBody: List[Stat],
      traitDefinition: Defn.Trait
  )

  private def characterize(
      traitName: String,
      typeParameterNames: List[String],
      selfMemberName: String,
      inputSelf: Self,
      originalBody: List[Stat],
      lowerBound: Boolean,
      fBound: Boolean
  ): Characterization =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTrees = typeParameterNames.map(Type.Name.apply)
    val typeParameters: List[Type.Param] =
      typeParameterNameTrees.map(name => tparam"$name")
    val typeArguments: List[Type] = typeParameterNameTrees
    val target: Type =
      if typeArguments.isEmpty then t"$traitNameTree"
      else t"$traitNameTree[..$typeArguments]"
    val outputSelfName =
      inputSelf.name match
        case Name.Anonymous() => Term.Name("self")
        case name: Term.Name => name
        case other => fail(s"expected anonymous or term self name, found ${other.structure}")
    val outputSelf = Self(outputSelfName, inputSelf.decltpe)
    val selfMemberNameTree = Type.Name(selfMemberName)
    val selectedSelfMember: Type =
      t"$outputSelfName.$selfMemberNameTree"
    val fBoundEquality: Option[Defn.Type] =
      Option.when(fBound)(
        q"type $selfMemberNameTree = $selectedSelfMember"
      )
    val upperBound: Type =
      fBoundEquality match
        case Some(equality) =>
          val refinementStats: List[Stat] = List(equality)
          t"$target { ..$refinementStats }"
        case None => target
    val singletonLowerBound: Option[Type] =
      Option.when(lowerBound)(t"$outputSelfName.type")
    val generatedMember: Decl.Type =
      singletonLowerBound match
        case Some(value) =>
          q"type $selfMemberNameTree >: $value <: $upperBound"
        case None =>
          q"type $selfMemberNameTree <: $upperBound"
    val outputStats: List[Stat] = generatedMember :: originalBody
    val outputSelfOption: Option[Self] = Some(outputSelf)
    val traitDefinition: Defn.Trait =
      q"trait $traitNameTree[..$typeParameters] { $outputSelfOption => ..$outputStats }"

    Characterization(
      inputSelf,
      outputSelf,
      target,
      singletonLowerBound,
      selectedSelfMember,
      fBoundEquality,
      upperBound,
      generatedMember,
      originalBody,
      traitDefinition
    )
