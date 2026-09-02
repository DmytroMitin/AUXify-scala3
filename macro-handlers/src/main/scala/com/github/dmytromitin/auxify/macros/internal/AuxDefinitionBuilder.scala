package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.AuxTypeAliasPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object AuxDefinitionBuilder:
  def definition(shape: AuxSourceShapeDecoder.Shape): Defn.Type =
    val aliasName = Type.Name("Aux")
    val typeClassName = Type.Name(shape.typeClassName)
    val firstTypeParameterName = Type.Name(shape.firstTypeParameterName)
    val secondTypeParameterName = Type.Name(shape.secondTypeParameterName)
    val upperBoundTypeName = Type.Name(shape.upperBoundTypeName)
    val resultTypeMemberName = Type.Name(shape.resultTypeMemberName)
    val generatedResultParameterName =
      Type.Name(shape.generatedResultParameterName)
    val firstTypeParameter: Type.Param =
      tparam"$firstTypeParameterName <: $upperBoundTypeName"
    val secondTypeParameter: Type.Param =
      tparam"$secondTypeParameterName <: $upperBoundTypeName"
    val generatedResultParameter: Type.Param =
      tparam"$generatedResultParameterName <: $upperBoundTypeName"
    val allTypeParameters =
      List(firstTypeParameter, secondTypeParameter, generatedResultParameter)
    val targetArguments: List[Type] =
      List(firstTypeParameterName, secondTypeParameterName)
    val target: Type = t"$typeClassName[..$targetArguments]"
    val resultEquality: Defn.Type =
      q"type $resultTypeMemberName = $generatedResultParameterName"
    val refinementMembers: List[Stat] = List(resultEquality)
    val refinement: Type = t"$target { ..$refinementMembers }"

    q"type $aliasName[..$allTypeParameters] = $refinement"

  def lower(
      shape: AuxSourceShapeDecoder.Shape
  )(using Context): Either[
    AuxTypeAliasPeerBridge.Failure,
    AuxTypeAliasPeerBridge.Lowered
  ] =
    val sourceDefinition = definition(shape)
    AuxTypeAliasPeerBridge.lower(
      sourceDefinition,
      expectedAliasName = "Aux",
      expectedFirstParameterName = shape.firstTypeParameterName,
      expectedFirstUpperBoundName = shape.upperBoundTypeName,
      expectedSecondParameterName = shape.secondTypeParameterName,
      expectedSecondUpperBoundName = shape.upperBoundTypeName,
      expectedOutputParameterName = shape.generatedResultParameterName,
      expectedOutputUpperBoundName = shape.upperBoundTypeName,
      expectedTargetName = shape.typeClassName,
      expectedRefinementMemberName = shape.resultTypeMemberName,
      virtualSourceName = s"AuxifyGenerated${shape.typeClassName}Aux.scala"
    )
