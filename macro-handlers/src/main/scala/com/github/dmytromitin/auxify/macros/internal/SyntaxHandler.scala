package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  DefinitionPlacement,
  ExpansionDiagnostic,
  ExpansionEdit,
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetKind
}
import paradise3.api.helpers.{
  ExpansionTransforms,
  MemberConflictPolicy,
  MissingCompanionPolicy
}

import quasiquotes.definitions.dotty.ExtensionModulePeerBridge

import scala.meta.Defn

final class SyntaxHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.syntax"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    SyntaxHandler.expandWithLowering(input):
      (definition, virtualSourceName, context) =>
        ExtensionModulePeerBridge.lower(
          definition,
          virtualSourceName
        )(using context)

private[internal] object SyntaxHandler:
  type Lowering = (
      Defn.Object,
      String,
      Context
  ) => Either[
    ExtensionModulePeerBridge.Failure,
    ExtensionModulePeerBridge.Lowered
  ]

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      for
        targetView <- input.targetView
        bodyView <- input.targetBodyView
        shape <- SyntaxSourceShapeDecoder.decode(targetView, bodyView)
        lowered <- lower(
          SyntaxDefinitionBuilder.module(shape),
          s"AuxifyGenerated${shape.traitName}Syntax.scala",
          summon[Context]
        ).left.map(failure =>
          ExpansionDiagnostic(
            s"${failure.code}: ${failure.detail}",
            input.currentAnnotation.sourcePos
          )
        )
        edit <- ExpansionEdit.start(input)
        updated <- ExpansionTransforms.placeMemberInCompanion(
          lowered.tree,
          MissingCompanionPolicy.Create(
            ExpansionTargetKind.Object,
            DefinitionPlacement.AfterPrimary
          ),
          MemberConflictPolicy.PreserveExisting
        )(edit)
      yield updated
