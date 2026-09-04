package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object InstanceDefinitionBuilder:
  def lower(
      shape: InstanceSourceShapeDecoder.SourceShape
  )(using Context): Either[
    InstanceFactoryPeerBridge.Failure,
    InstanceFactoryPeerBridge.Lowered
  ] =
    InstanceFactoryPeerBridge.lower(
      definition(shape),
      s"AuxifyGenerated${shape.traitName}Instance.scala"
    )

  def definition(
      shape: InstanceSourceShapeDecoder.SourceShape
  ): Defn.Def =
    val traitName = Type.Name(shape.traitName)
    val typeParameterName = Type.Name(shape.enclosingTypeParameterName)
    val factoryName = Term.Name("instance")
    val parameterlessMethodName = Term.Name(shape.parameterlessMethodName)
    val binaryMethodName = Term.Name(shape.binaryMethodName)
    val binaryFirstParameterName = Term.Name(shape.binaryFirstParameterName)
    val binarySecondParameterName = Term.Name(shape.binarySecondParameterName)
    val parameterlessCarrierName = Term.Name(shape.parameterlessCarrierName)
    val binaryCarrierName = Term.Name(shape.binaryCarrierName)

    val typeParameter: Type.Param = tparam"$typeParameterName"
    val typeArguments: List[Type] = List(typeParameterName)
    val target: Type = t"$traitName[..$typeArguments]"
    val parameterlessCarrierType: Type = t"=> $typeParameterName"
    val binaryCarrierType: Type =
      t"($typeParameterName, $typeParameterName) => $typeParameterName"
    val parameterlessCarrier: Term.Param =
      param"$parameterlessCarrierName: $parameterlessCarrierType"
    val binaryCarrier: Term.Param =
      param"$binaryCarrierName: $binaryCarrierType"
    val binaryFirstParameter: Term.Param =
      param"$binaryFirstParameterName: $typeParameterName"
    val binarySecondParameter: Term.Param =
      param"$binarySecondParameterName: $typeParameterName"
    val parameterlessOverride: Defn.Def =
      q"override def $parameterlessMethodName: $typeParameterName = $parameterlessCarrierName"
    val binaryOverride: Defn.Def =
      q"override def $binaryMethodName($binaryFirstParameter, $binarySecondParameter): $typeParameterName = $binaryCarrierName($binaryFirstParameterName, $binarySecondParameterName)"
    val overrideStats: List[Stat] =
      List(parameterlessOverride, binaryOverride)
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementation: Term.NewAnonymous =
      q"new $parent { ..$overrideStats }"
    val typeParameters = List(typeParameter)
    val factoryParameters = List(parameterlessCarrier, binaryCarrier)

    q"def $factoryName[..$typeParameters](..$factoryParameters): $target = $implementation"
