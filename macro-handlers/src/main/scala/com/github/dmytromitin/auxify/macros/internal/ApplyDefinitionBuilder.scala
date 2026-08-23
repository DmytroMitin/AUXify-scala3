package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.annotation.nowarn
import scala.meta.*
import scala.meta.dialects.Scala3

@nowarn("cat=deprecation")
private[internal] object ApplyDefinitionBuilder:
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
