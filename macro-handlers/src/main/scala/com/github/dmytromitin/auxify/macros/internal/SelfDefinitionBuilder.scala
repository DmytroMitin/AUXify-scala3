package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

import scala.annotation.nowarn
import scala.meta.*

@nowarn("cat=deprecation")
private[internal] object SelfDefinitionBuilder:
  def definition(traitName: String, selfAliasName: String): Decl.Type =
    val memberName = Type.Name("Self")
    val traitNameTree = Type.Name(traitName)
    val selfAliasNameTree = Term.Name(selfAliasName)
    val lowerBound: Type = t"$selfAliasNameTree.type"
    val selectedSelfMember: Type = t"$selfAliasNameTree.$memberName"
    val equality: Defn.Type = q"type $memberName = $selectedSelfMember"
    val refinements: List[Stat] = equality :: Nil
    val upperBound: Type = t"$traitNameTree { ..$refinements }"

    q"type $memberName >: $lowerBound <: $upperBound"

  def lower(
      traitName: String,
      selfAliasName: String
  )(using Context): Either[
    SelfAbstractTypeMemberPeerBridge.Failure,
    SelfAbstractTypeMemberPeerBridge.Lowered
  ] =
    SelfAbstractTypeMemberPeerBridge.lower(
      definition(traitName, selfAliasName),
      expectedMemberName = "Self",
      expectedSelfAliasName = selfAliasName,
      expectedUpperBaseName = traitName,
      virtualSourceName = s"AuxifyGenerated${traitName}Self.scala"
    )
