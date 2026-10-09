package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object InstanceCurriedMethodDefinitionBuilder:
  def lower(
      shape: InstanceCurriedMethodSourceShapeDecoder.SourceShape
  )(using Context): Either[
    InstanceFactoryPeerBridge.Failure,
    InstanceFactoryPeerBridge.Lowered
  ] =
    InstanceFactoryPeerBridge.lower(
      definition(shape),
      s"AuxifyGenerated${shape.traitName}Instance.scala"
    )

  def definition(
      shape: InstanceCurriedMethodSourceShapeDecoder.SourceShape
  ): Defn.Def =
    val traitName = Type.Name(shape.traitName)
    val typeParameterName = Type.Name(shape.enclosingTypeParameterName)
    val methodName = Term.Name(shape.methodName)
    val firstParameterName = Term.Name(shape.firstParameterName)
    val secondParameterName = Term.Name(shape.secondParameterName)
    val carrierName = Term.Name(shape.carrierName)
    val factoryName = Term.Name("instance")

    val typeParameter: Type.Param = tparam"$typeParameterName"
    val target: Type = t"$traitName[$typeParameterName]"
    val carrierType: Type =
      Type.Function(
        List(typeParameterName),
        Type.Function(List(typeParameterName), typeParameterName)
      )
    val carrier: Term.Param = param"$carrierName: $carrierType"
    val firstParameter: Term.Param =
      param"$firstParameterName: $typeParameterName"
    val secondParameter: Term.Param =
      param"$secondParameterName: $typeParameterName"
    val body: Term =
      Term.Apply(
        Term.Apply(carrierName, List(firstParameterName)),
        List(secondParameterName)
      )
    val overrideMember: Defn.Def =
      q"override def $methodName($firstParameter)($secondParameter): $typeParameterName = $body"
    val parent = Init(target, Name.Anonymous(), List.empty[Term.ArgClause])
    val implementation: Term.NewAnonymous =
      q"new $parent { ..${List[Stat](overrideMember)} }"

    q"def $factoryName[$typeParameter]($carrier): $target = $implementation"
