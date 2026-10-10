package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  DefinitionPlacement,
  ExpansionDiagnostic,
  ExpansionEdit,
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetBodyView,
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
        _ <-
          if
            bodyView.members.sizeCompare(1) > 0 &&
              input.sourceOrderedHandledAnnotationNames.contains(
                "com.github.dmytromitin.auxify.macros.apply"
              )
          then
            Left(
              ExpansionDiagnostic(
                s"unsupported @delegated composition shape for `${input.primary.name}`: inherited concrete tails are not supported when stacked with @apply",
                input.currentAnnotation.sourcePos
              )
            )
          else Right(())
        typeStructure <-
          if bodyView.members.exists(
              _.kind == ExpansionTargetBodyView.DirectMemberKind.Type
            ) then input.targetTypeStructureView.map(Some(_))
          else Right(None)
        shape <- DelegatedSourceShapeDecoder.decode(
          input.primary.name,
          classView,
          bodyView,
          typeStructure
        )
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
