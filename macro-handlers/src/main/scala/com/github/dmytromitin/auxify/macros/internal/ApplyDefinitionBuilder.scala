package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

import scala.meta.*

private[internal] object ApplyDefinitionBuilder:
  def definition(className: String, typeParameterName: String): Defn.Def =
    val tparam = Type.Param(
      Nil,
      Type.Name(typeParameterName),
      Type.ParamClause(Nil),
      Type.Bounds(None, None, Nil, Nil)
    )

    val target = Type.Apply(
      Type.Name(className),
      Type.ArgClause(List(Type.Name(typeParameterName)))
    )

    val inst = Term.Param(
      Nil,
      Term.Name("inst"),
      Some(target),
      None
    )

    val clauses = Member.ParamClauseGroup(
      Type.ParamClause(List(tparam)),
      List(Term.ParamClause(List(inst), Some(Mod.Using())))
    )

    Defn.Def(
      Nil,
      Term.Name("apply"),
      List(clauses),
      Some(target),
      Term.Name("inst")
    )

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
