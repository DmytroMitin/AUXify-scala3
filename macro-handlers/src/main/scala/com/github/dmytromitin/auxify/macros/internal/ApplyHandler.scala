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

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

final class ApplyHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.apply"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      input.targetView.flatMap: view =>
        AuxifyTargetAdmission.applyTarget(view).flatMap: _ =>
          view.typeParameters match
            case List(typeParameter) =>
              lowerAndPlace(
                input,
                ApplyDefinitionBuilder.lower(input.primary.name, typeParameter.name)
              )
            case List(_, _) =>
              input.targetTypeStructureView.flatMap: structure =>
                ApplyFullShapeDecoder.decode(input.primary.name, structure).flatMap: shape =>
                  lowerAndPlace(input, ApplyDefinitionBuilder.lowerFull(shape))
            case _ =>
              Left(
                ExpansionDiagnostic(
                  s"unsupported @apply source shape for `${input.primary.name}`",
                  input.currentAnnotation.sourcePos
                )
              )

  private def lowerAndPlace(
      input: ExpansionInput,
      lowered: Either[
        ContextualMethodPeerBridge.Failure,
        ContextualMethodPeerBridge.Lowered
      ]
  )(using Context): Either[ExpansionDiagnostic, ExpansionEdit] =
    lowered
      .left.map(failure =>
        ExpansionDiagnostic(
          s"${failure.code}: ${failure.detail}",
          input.currentAnnotation.sourcePos
        )
      )
      .flatMap: value =>
        ExpansionEdit.start(input).flatMap(
          ExpansionTransforms.placeMemberInCompanion(
            value.tree,
            MissingCompanionPolicy.Create(
              ExpansionTargetKind.Object,
              DefinitionPlacement.AfterPrimary
            ),
            MemberConflictPolicy.PreserveExisting
          )
        )
