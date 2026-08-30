package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.{Context, ContextBase}

import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

import scala.meta.*

class SelfDefinitionBuilderSuite extends munit.FunSuite:
  test("definition derives the exact default Self member from the prepared alias") {
    val cases = List(
      ("Nat", "self", "type Self >: self.type <: Nat {\n  type Self = self.Self\n}"),
      ("NamedNat", "stable", "type Self >: stable.type <: NamedNat {\n  type Self = stable.Self\n}"),
      ("CollisionNat", "self$2", "type Self >: self$2.type <: CollisionNat {\n  type Self = self$2.Self\n}")
    )

    cases.foreach: (traitName, aliasName, expectedSyntax) =>
      val declaration: Decl.Type =
        SelfDefinitionBuilder.definition(traitName, aliasName)

      assertEquals(declaration.syntax, expectedSyntax)
      assertEquals(declaration.name.value, "Self")
      assertEquals(declaration.bounds.lo.map(_.syntax), Some(s"$aliasName.type"))
      assertEquals(declaration.bounds.hi.map(_.syntax), Some(expectedSyntax.stripPrefix("type Self >: " + aliasName + ".type <: ")))
  }

  test("lower delegates the collision-safe declaration to the exact peer bridge") {
    withContext {
      val lowered: SelfAbstractTypeMemberPeerBridge.Lowered =
        SelfDefinitionBuilder
          .lower("CollisionNat", "self$2")
          .fold(failure => fail(s"${failure.code}: ${failure.detail}"), identity)

      assertEquals(
        lowered.generatedSource,
        "type Self >: self$2.type <: CollisionNat { type Self = self$2.Self }"
      )
      assertEquals(
        lowered.virtualSourceName,
        "AuxifyGeneratedCollisionNatSelf.scala"
      )
      assertEquals(lowered.tree.name.toString, "Self")
      lowered.tree.rhs match
        case untpd.TypeBoundsTree(
              untpd.SingletonTypeTree(untpd.Ident(lowerAlias)),
              untpd.RefinedTypeTree(
                untpd.Ident(upperBase),
                List(refinement: untpd.TypeDef)
              ),
              _
            ) =>
          assertEquals(lowerAlias.toString, "self$2")
          assertEquals(upperBase.toString, "CollisionNat")
          assertEquals(refinement.name.toString, "Self")
        case other => fail(s"expected exact bounded refined Self TypeDef, found $other")
    }
  }

  private def withContext[A](run: Context ?=> A): A =
    val base = new ContextBase
    run(using base.initialCtx)
