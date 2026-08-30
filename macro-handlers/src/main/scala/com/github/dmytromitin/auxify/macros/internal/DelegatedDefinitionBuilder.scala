package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.DelegatedForwardingMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object DelegatedDefinitionBuilder:
  def definition(shape: DelegatedSourceShapeDecoder.SourceShape): Defn.Def =
    val traitName = Type.Name(shape.traitName)
    val typeParameterName = Type.Name(shape.typeParameterName)
    val methodName = Term.Name(shape.methodName)
    val parameterName = Term.Name(shape.parameterName)
    val resultTypeName = Type.Name(shape.resultTypeName)
    val evidenceName = Term.Name(freshEvidenceName(Set(shape.parameterName)))
    val typeParameter: Type.Param = tparam"$typeParameterName"
    val typeParameters: List[Type.Param] = typeParameter :: Nil
    val typeArguments: List[Type] = typeParameterName :: Nil
    val target: Type = t"$traitName[..$typeArguments]"
    val ordinaryParameter: Term.Param = param"$parameterName: $typeParameterName"
    val invocation: Term = q"$evidenceName.$methodName($parameterName)"

    q"def $methodName[..$typeParameters]($ordinaryParameter)(using $evidenceName: $target): $resultTypeName = $invocation"

  def lower(
      shape: DelegatedSourceShapeDecoder.SourceShape
  )(using Context): Either[
    DelegatedForwardingMethodPeerBridge.Failure,
    DelegatedForwardingMethodPeerBridge.Lowered
  ] =
    DelegatedForwardingMethodPeerBridge.lower(
      definition(shape),
      s"AuxifyGenerated${shape.traitName}Delegated.scala"
    )

  private def freshEvidenceName(occupied: Set[String]): String =
    (0 to occupied.size)
      .iterator
      .map(index => if index == 0 then "inst" else s"inst$index")
      .find(name => !occupied.contains(name))
      .getOrElse("inst")
