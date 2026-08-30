package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object ApplyDefinitionBuilder:
  final case class FullShape(
      typeClassName: String,
      firstTypeParameterName: String,
      secondTypeParameterName: String,
      upperBoundTypeName: String,
      resultTypeMemberName: String
  )

  def definition(className: String, typeParameterName: String): Defn.Def =
    val classNameTree = Type.Name(className)
    val typeParameterNameTree = Type.Name(typeParameterName)
    val typeParameter = tparam"$typeParameterNameTree"
    val target = t"$classNameTree[$typeParameterNameTree]"

    q"def apply[$typeParameter](using inst: $target): $target = inst"

  def lower(
      className: String,
      typeParameterName: String
  )(using Context): Either[
    ContextualMethodPeerBridge.Failure,
    ContextualMethodPeerBridge.Lowered
  ] =
    ContextualMethodPeerBridge.lower(
      definition(className, typeParameterName),
      s"AuxifyGenerated${className}Apply.scala"
    )

  def fullDefinition(shape: FullShape): Defn.Def =
    val typeClassName = Type.Name(shape.typeClassName)
    val firstTypeParameterName = Type.Name(shape.firstTypeParameterName)
    val secondTypeParameterName = Type.Name(shape.secondTypeParameterName)
    val upperBoundTypeName = Type.Name(shape.upperBoundTypeName)
    val resultTypeMemberName = Type.Name(shape.resultTypeMemberName)
    val contextualParameterName = Term.Name("inst")
    val firstTypeParameter: Type.Param =
      tparam"$firstTypeParameterName <: $upperBoundTypeName"
    val secondTypeParameter: Type.Param =
      tparam"$secondTypeParameterName <: $upperBoundTypeName"
    val target: Type =
      t"$typeClassName[..${List(firstTypeParameterName, secondTypeParameterName)}]"
    val selectedResultType: Type =
      t"$contextualParameterName.$resultTypeMemberName"
    val refinedResultType: Type =
      t"$target { type $resultTypeMemberName = $selectedResultType }"

    q"def apply[..${List(firstTypeParameter, secondTypeParameter)}](using $contextualParameterName: $target): $refinedResultType = $contextualParameterName"

  def lowerFull(
      shape: FullShape
  )(using Context): Either[
    ContextualMethodPeerBridge.Failure,
    ContextualMethodPeerBridge.Lowered
  ] =
    ContextualMethodPeerBridge.lower(
      fullDefinition(shape),
      s"AuxifyGenerated${shape.typeClassName}Apply.scala"
    )
