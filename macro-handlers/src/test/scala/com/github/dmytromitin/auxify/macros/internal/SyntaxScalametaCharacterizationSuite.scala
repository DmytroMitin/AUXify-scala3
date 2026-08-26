package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

private object ImplicitClassSyntaxFixture:
  trait Monoid[A]:
    def combine(a: A, a1: A): A

  object Monoid:
    object syntax:
      implicit class Ops[A](a: A):
        def combine(a1: A)(using inst: Monoid[A]): A =
          inst.combine(a, a1)

    given Monoid[Int] with
      def combine(a: Int, a1: Int): Int = a + a1

  def result: Int =
    import Monoid.syntax.*
    2.combine(3)

private object ExtensionSyntaxFixture:
  trait Monoid[A]:
    def combine(a: A, a1: A): A

  object Monoid:
    object syntax:
      extension [A](a: A)
        def combine(a1: A)(using inst: Monoid[A]): A =
          inst.combine(a, a1)

    given Monoid[Int] with
      def combine(a: Int, a1: Int): Int = a + a1

  def result: Int =
    import Monoid.syntax.*
    2.combine(3)

@nowarn("cat=deprecation")
class SyntaxScalametaCharacterizationSuite extends munit.FunSuite:
  private val CanonicalImplicitClassSource =
    "object syntax { implicit class Ops[A](a: A) { def combine(a1: A)(using inst: Monoid[A]): A = inst.combine(a, a1) } }"

  private val CanonicalExtensionSource =
    """object syntax {
      |  extension [A](a: A) {
      |    def combine(a1: A)(using inst: Monoid[A]): A = inst.combine(a, a1)
      |  }
      |}""".stripMargin

  test("literal implicit-class candidate preserves the receiver forwarder and use shape") {
    val candidate = characterizeImplicitClass(
      traitName = "Monoid",
      typeParameterName = "A",
      wrapperName = "Ops",
      methodName = "combine",
      receiverName = "a",
      remainingParameterName = "a1",
      evidenceName = "inst"
    )

    assertEquals(candidate.syntaxObject.syntax, CanonicalImplicitClassSource)
    assertEquals(candidate.typeParameter.syntax, "A")
    assertEquals(candidate.target.syntax, "Monoid[A]")
    assertEquals(candidate.receiverParameter.syntax, "a: A")
    assertEquals(candidate.remainingParameter.syntax, "a1: A")
    assertEquals(candidate.evidenceParameter.syntax, "inst: Monoid[A]")
    assert(candidate.wrapper.mods.exists(_.isInstanceOf[Mod.Implicit]))
    assertEquals(candidate.wrapper.name.value, "Ops")
    assertForwardingBody(candidate.forwardingMethod.body, "inst", "combine", List("a", "a1"))
    assertEquals(ImplicitClassSyntaxFixture.result, 5)
  }

  test("Scala 3 extension candidate preserves the receiver forwarder and use shape") {
    val candidate = characterizeExtension(
      traitName = "Monoid",
      typeParameterName = "A",
      methodName = "combine",
      receiverName = "a",
      remainingParameterName = "a1",
      evidenceName = "inst"
    )

    assertEquals(candidate.syntaxObject.syntax, CanonicalExtensionSource)
    assertEquals(candidate.typeParameter.syntax, "A")
    assertEquals(candidate.target.syntax, "Monoid[A]")
    assertEquals(candidate.receiverParameter.syntax, "a: A")
    assertEquals(candidate.remainingParameter.syntax, "a1: A")
    assertEquals(candidate.evidenceParameter.syntax, "inst: Monoid[A]")
    assertEquals(candidate.extensionGroup.tparams.map(_.syntax), List("A"))
    assertEquals(candidate.extensionGroup.paramss.flatten.map(_.syntax), List("a: A"))
    assertForwardingBody(candidate.forwardingMethod.body, "inst", "combine", List("a", "a1"))
    assertEquals(ExtensionSyntaxFixture.result, 5)
  }

  test("both candidates derive dynamic trait method type receiver argument and evidence names") {
    val implicitClass = characterizeImplicitClass(
      traitName = "Merge",
      typeParameterName = "Value",
      wrapperName = "MergeOps",
      methodName = "merge",
      receiverName = "left",
      remainingParameterName = "right",
      evidenceName = "typeClass"
    )
    val extension = characterizeExtension(
      traitName = "Merge",
      typeParameterName = "Value",
      methodName = "merge",
      receiverName = "left",
      remainingParameterName = "right",
      evidenceName = "typeClass"
    )

    val expectedImplicitClass =
      "object syntax { implicit class MergeOps[Value](left: Value) { def merge(right: Value)(using typeClass: Merge[Value]): Value = typeClass.merge(left, right) } }"
    val expectedExtension =
      """object syntax {
        |  extension [Value](left: Value) {
        |    def merge(right: Value)(using typeClass: Merge[Value]): Value = typeClass.merge(left, right)
        |  }
        |}""".stripMargin

    assertEquals(implicitClass.syntaxObject.syntax, expectedImplicitClass)
    assertEquals(extension.syntaxObject.syntax, expectedExtension)
  }

  private final case class ImplicitClassCandidate(
      typeParameter: Type.Param,
      target: Type,
      receiverParameter: Term.Param,
      remainingParameter: Term.Param,
      evidenceParameter: Term.Param,
      forwardingMethod: Defn.Def,
      wrapper: Defn.Class,
      syntaxObject: Defn.Object
  )

  private final case class ExtensionCandidate(
      typeParameter: Type.Param,
      target: Type,
      receiverParameter: Term.Param,
      remainingParameter: Term.Param,
      evidenceParameter: Term.Param,
      forwardingMethod: Defn.Def,
      extensionGroup: Defn.ExtensionGroup,
      syntaxObject: Defn.Object
  )

  private def characterizeImplicitClass(
      traitName: String,
      typeParameterName: String,
      wrapperName: String,
      methodName: String,
      receiverName: String,
      remainingParameterName: String,
      evidenceName: String
  ): ImplicitClassCandidate =
    val components = forwardingComponents(
      traitName,
      typeParameterName,
      methodName,
      receiverName,
      remainingParameterName,
      evidenceName
    )
    val wrapperNameTree = Type.Name(wrapperName)
    val typeParameters = List(components.typeParameter)
    val wrapperStats: List[Stat] = List(components.forwardingMethod)
    val wrapper: Defn.Class =
      q"implicit class $wrapperNameTree[..$typeParameters](${components.receiverParameter}) { ..$wrapperStats }"
    val syntaxStats: List[Stat] = List(wrapper)
    val syntaxObject: Defn.Object = q"object syntax { ..$syntaxStats }"

    ImplicitClassCandidate(
      components.typeParameter,
      components.target,
      components.receiverParameter,
      components.remainingParameter,
      components.evidenceParameter,
      components.forwardingMethod,
      wrapper,
      syntaxObject
    )

  private def characterizeExtension(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      receiverName: String,
      remainingParameterName: String,
      evidenceName: String
  ): ExtensionCandidate =
    val components = forwardingComponents(
      traitName,
      typeParameterName,
      methodName,
      receiverName,
      remainingParameterName,
      evidenceName
    )
    val typeParameters = List(components.typeParameter)
    val extensionGroup: Defn.ExtensionGroup =
      q"extension [..$typeParameters](${components.receiverParameter}) { ${components.forwardingMethod} }"
    val syntaxStats: List[Stat] = List(extensionGroup)
    val syntaxObject: Defn.Object = q"object syntax { ..$syntaxStats }"

    ExtensionCandidate(
      components.typeParameter,
      components.target,
      components.receiverParameter,
      components.remainingParameter,
      components.evidenceParameter,
      components.forwardingMethod,
      extensionGroup,
      syntaxObject
    )

  private final case class ForwardingComponents(
      typeParameter: Type.Param,
      target: Type,
      receiverParameter: Term.Param,
      remainingParameter: Term.Param,
      evidenceParameter: Term.Param,
      forwardingMethod: Defn.Def
  )

  private def forwardingComponents(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      receiverName: String,
      remainingParameterName: String,
      evidenceName: String
  ): ForwardingComponents =
    val traitNameTree = Type.Name(traitName)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val methodNameTree = Term.Name(methodName)
    val receiverNameTree = Term.Name(receiverName)
    val remainingParameterNameTree = Term.Name(remainingParameterName)
    val evidenceNameTree = Term.Name(evidenceName)

    val typeParameter: Type.Param = tparam"$typeParameterNameTree"
    val target: Type = t"$traitNameTree[$typeParameterNameTree]"
    val receiverParameter: Term.Param =
      param"$receiverNameTree: $typeParameterNameTree"
    val remainingParameter: Term.Param =
      param"$remainingParameterNameTree: $typeParameterNameTree"
    val evidenceParameter: Term.Param = param"$evidenceNameTree: $target"
    val invocation: Term =
      q"$evidenceNameTree.$methodNameTree($receiverNameTree, $remainingParameterNameTree)"
    val forwardingMethod: Defn.Def =
      q"def $methodNameTree($remainingParameter)(using $evidenceNameTree: $target): $typeParameterNameTree = $invocation"

    ForwardingComponents(
      typeParameter,
      target,
      receiverParameter,
      remainingParameter,
      evidenceParameter,
      forwardingMethod
    )

  private def assertForwardingBody(
      body: Term,
      expectedReceiver: String,
      expectedMethod: String,
      expectedArguments: List[String]
  ): Unit =
    body match
      case application: Term.Apply =>
        application.fun match
          case Term.Select(Term.Name(receiver), Term.Name(method)) =>
            assertEquals(receiver, expectedReceiver)
            assertEquals(method, expectedMethod)
          case other => fail(s"expected selected forwarding method, found ${other.structure}")
        assertEquals(
          application.argClause.values.collect { case Term.Name(value) => value },
          expectedArguments
        )
      case other => fail(s"expected forwarding application, found ${other.structure}")
