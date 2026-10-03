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

import quasiquotes.definitions.dotty.DelegatedForwardingMethodPeerBridge

final class DelegatedHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.delegated"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    DelegatedHandler.expandWithLowering(input): (shape, context) =>
      DelegatedDefinitionBuilder.lower(shape)(using context)

private[internal] object DelegatedHandler:
  type Lowering = (
      DelegatedSourceShapeDecoder.SourceShape,
      Context
  ) => Either[
    DelegatedForwardingMethodPeerBridge.Failure,
    DelegatedForwardingMethodPeerBridge.Lowered
  ]

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      for
        classView <- input.targetView
        _ <- AuxifyTargetAdmission.instanceOrDelegatedTarget(
          classView,
          "@delegated"
        )
        bodyView <- input.targetBodyView
        shape <- DelegatedSourceShapeDecoder.decode(input.primary.name, classView, bodyView)
        lowered <- lower(shape, summon[Context]).left.map(failure =>
          ExpansionDiagnostic(
            s"${failure.code}: ${failure.detail}",
            input.currentAnnotation.sourcePos
          )
        )
        edit <- ExpansionEdit.start(input)
        placed <- ExpansionTransforms.placeMemberInCompanion(
          lowered.tree,
          MissingCompanionPolicy.Create(
            ExpansionTargetKind.Object,
            DefinitionPlacement.AfterPrimary
          ),
          MemberConflictPolicy.PreserveExisting
        )(edit)
      yield placed
