package com.github.dmytromitin.auxify.macros.internal

import scala.annotation.nowarn
import scala.meta.*

@nowarn("cat=deprecation")
class SyntaxDefinitionBuilderSuite extends munit.FunSuite:
  test("builds the canonical complete native extension module") {
    val module = SyntaxDefinitionBuilder.module(
      shape("Monoid", "A", "combine", "a", "a1", "inst")
    )

    assertEquals(
      module.syntax,
      """object syntax {
        |  extension [A](a: A) {
        |    def combine(a1: A)(using inst: Monoid[A]): A = inst.combine(a, a1)
        |  }
        |}""".stripMargin
    )
    assertTypedTopology(module, "Monoid", "A", "combine", "a", "a1", "inst")
  }

  test("derives the complete module from coherently renamed semantic names") {
    val module = SyntaxDefinitionBuilder.module(
      shape("Merge", "Value", "merge", "left", "right", "evidence")
    )

    assertEquals(
      module.syntax,
      """object syntax {
        |  extension [Value](left: Value) {
        |    def merge(right: Value)(using evidence: Merge[Value]): Value = evidence.merge(left, right)
        |  }
        |}""".stripMargin
    )
    assertTypedTopology(
      module,
      "Merge",
      "Value",
      "merge",
      "left",
      "right",
      "evidence"
    )
  }

  test("uses the decoder-selected collision-free evidence name throughout") {
    val module = SyntaxDefinitionBuilder.module(
      shape("Collision", "Element", "merge", "inst", "inst1", "inst2")
    )

    assertEquals(
      module.syntax,
      """object syntax {
        |  extension [Element](inst: Element) {
        |    def merge(inst1: Element)(using inst2: Collision[Element]): Element = inst2.merge(inst, inst1)
        |  }
        |}""".stripMargin
    )
    assertTypedTopology(
      module,
      "Collision",
      "Element",
      "merge",
      "inst",
      "inst1",
      "inst2"
    )
  }

  private def shape(
      traitName: String,
      typeParameterName: String,
      methodName: String,
      receiverName: String,
      remainingName: String,
      evidenceName: String
  ): SyntaxSourceShapeDecoder.SourceShape =
    SyntaxSourceShapeDecoder.SourceShape(
      traitName,
      typeParameterName,
      typeParameterName,
      methodName,
      receiverName,
      remainingName,
      evidenceName
    )

  private def assertTypedTopology(
      module: Defn.Object,
      expectedTraitName: String,
      expectedTypeParameterName: String,
      expectedMethodName: String,
      expectedReceiverName: String,
      expectedRemainingName: String,
      expectedEvidenceName: String
  ): Unit =
    assertEquals(module.name.value, "syntax")
    assertEquals(module.templ.inits, Nil)
    module.templ.stats match
      case List(extension: Defn.ExtensionGroup) =>
        assertEquals(
          extension.tparams.map(_.name.value),
          List(expectedTypeParameterName)
        )
        extension.paramss match
          case List(List(receiver)) =>
            assertEquals(receiver.name.value, expectedReceiverName)
            assertEquals(
              receiver.decltpe.map(_.syntax),
              Some(expectedTypeParameterName)
            )
          case other => fail(s"expected one extension receiver, found $other")

        val method = extension.body match
          case Term.Block(List(value: Defn.Def)) => value
          case other => fail(s"expected one forwarding method block, found $other")

        assertEquals(method.name.value, expectedMethodName)
        assertEquals(method.decltpe.map(_.syntax), Some(expectedTypeParameterName))
        method.paramClauseGroups match
          case group :: Nil =>
            assertEquals(group.tparamClause.values, Nil)
            group.paramClauses match
              case ordinary :: contextual :: Nil =>
                ordinary.values match
                  case remaining :: Nil =>
                    assertEquals(remaining.name.value, expectedRemainingName)
                    assertEquals(
                      remaining.decltpe.map(_.syntax),
                      Some(expectedTypeParameterName)
                    )
                  case other => fail(s"expected one remaining parameter, found $other")
                assert(contextual.mod.exists(_.isInstanceOf[Mod.Using]))
                contextual.values match
                  case evidence :: Nil =>
                    assertEquals(evidence.name.value, expectedEvidenceName)
                    evidence.decltpe match
                      case Some(applied: Type.Apply) =>
                        assertEquals(applied.tpe.syntax, expectedTraitName)
                        assertEquals(
                          applied.argClause.values.map(_.syntax),
                          List(expectedTypeParameterName)
                        )
                      case other => fail(s"expected applied evidence type, found $other")
                  case other => fail(s"expected one contextual evidence parameter, found $other")
              case other => fail(s"expected ordinary and final contextual clauses, found $other")
          case other => fail(s"expected one method parameter group, found $other")

        method.body match
          case application: Term.Apply =>
            application.fun match
              case Term.Select(Term.Name(receiver), Term.Name(selectedMethod)) =>
                assertEquals(receiver, expectedEvidenceName)
                assertEquals(selectedMethod, expectedMethodName)
              case other => fail(s"expected selected evidence method, found ${other.structure}")
            assertEquals(
              application.argClause.values.map(_.syntax),
              List(expectedReceiverName, expectedRemainingName)
            )
          case other => fail(s"expected forwarding application, found ${other.structure}")
      case List(_: Defn.Class) => fail("implicit-class wrapper representation is forbidden")
      case other => fail(s"expected exactly one native extension group, found $other")
