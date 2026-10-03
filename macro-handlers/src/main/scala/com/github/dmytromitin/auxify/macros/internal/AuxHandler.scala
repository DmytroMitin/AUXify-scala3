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

import quasiquotes.definitions.dotty.AuxTypeAliasPeerBridge

final class AuxHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.aux"

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    AuxHandler.expandWithLowering(input): (shape, context) =>
      AuxDefinitionBuilder.lower(shape)(using context)

private[internal] object AuxHandler:
  type Lowering = (
      AuxSourceShapeDecoder.Shape,
      Context
  ) => Either[
    AuxTypeAliasPeerBridge.Failure,
    AuxTypeAliasPeerBridge.Lowered
  ]

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    ExpansionEdit.finish:
      for
        view <- input.targetView
        _ <- AuxifyTargetAdmission.auxTarget(view)
        structure <- input.targetTypeStructureView
        shape <- AuxSourceShapeDecoder.decode(input.primary.name, structure)
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
