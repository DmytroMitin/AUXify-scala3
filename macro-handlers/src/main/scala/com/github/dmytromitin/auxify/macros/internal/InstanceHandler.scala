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

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

final class InstanceHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.instance"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    InstanceHandler.expandWithLowering(input): (shape, context) =>
      InstanceDefinitionBuilder.lower(shape)(using context)

private[internal] object InstanceHandler:
  type Lowering = (
      InstanceSourceShapeDecoder.SourceShape,
      Context
  ) => Either[
    InstanceFactoryPeerBridge.Failure,
    InstanceFactoryPeerBridge.Lowered
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
          "@instance"
        )
        bodyView <- input.targetBodyView
        typeStructure <-
          if bodyView.members.drop(2).exists(
              _.kind == ExpansionTargetBodyView.DirectMemberKind.Type
            ) then input.targetTypeStructureView.map(Some(_))
          else Right(None)
        shape <- InstanceSourceShapeDecoder.decode(
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
