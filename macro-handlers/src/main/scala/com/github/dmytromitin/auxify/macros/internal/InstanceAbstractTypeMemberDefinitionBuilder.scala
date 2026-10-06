package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object InstanceAbstractTypeMemberDefinitionBuilder:
  def lower(
      shape: InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape
  )(using Context): Either[
    InstanceFactoryPeerBridge.Failure,
    InstanceFactoryPeerBridge.Lowered
  ] =
    InstanceFactoryPeerBridge.lower(
      definition(shape),
      s"AuxifyGenerated${shape.traitName}Instance.scala"
    )

  def definition(
      shape: InstanceAbstractTypeMemberSourceShapeDecoder.SourceShape
  ): Defn.Def =
    val traitName = Type.Name(shape.traitName)
    val enclosingTypeName = Type.Name(shape.enclosingTypeParameterName)
    val memberName = Type.Name(shape.memberName)
    val generatedTypeName = Type.Name(shape.generatedTypeParameterName)
    val factoryName = Term.Name("instance")

    val enclosingTypeParameter: Type.Param = tparam"$enclosingTypeName"
    val generatedTypeParameter: Type.Param = tparam"$generatedTypeName"
    val target: Type = t"$traitName[$enclosingTypeName]"
    val refinedReturnType: Type =
      t"$target { type $memberName = $generatedTypeName }"
    val implementationAlias: Defn.Type =
      q"type $memberName = $generatedTypeName"
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementationStats: List[Stat] = List(implementationAlias)
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$implementationStats }"
    val typeParameters = List(enclosingTypeParameter, generatedTypeParameter)

    q"def $factoryName[..$typeParameters]: $refinedReturnType = $implementation"
