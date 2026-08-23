package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object ApplyDefinitionBuilder:
  def definition(className: String, typeParameterName: String): Defn.Def =
    val typeParameter = Type.Param(
      Nil,
      Type.Name(typeParameterName),
      Type.ParamClause(Nil),
      Type.Bounds(None, None, Nil, Nil)
    )
    val typeParameters = List(typeParameter)
    val target: Type = Type.Apply(
      Type.Name(className),
      Type.ArgClause(List(Type.Name(typeParameterName)))
    )

    q"def apply[..$typeParameters](using inst: $target): $target = inst"
      .asInstanceOf[Defn.Def]

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
