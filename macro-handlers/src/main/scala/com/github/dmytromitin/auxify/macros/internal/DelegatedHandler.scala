package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  ParadiseAnnotationExpander
}
import paradise3.api.helpers.{
  CompanionMethodConflictPolicy,
  ExpansionHelpers
}

import quasiquotes.definitions.dotty.DelegatedForwardingMethodPeerBridge

final class DelegatedHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.delegated"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.RestrictedGenericTraitApply

  override val consumesExistingCompanion: Boolean = true

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
    ExpansionHelpers.withAnnotatedClassView(input): classView =>
      input.annotatedClassBodyView match
        case Left(diagnostic) =>
          ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
        case Right(bodyView) =>
          DelegatedSourceShapeDecoder.decode(input.className, classView, bodyView) match
            case Left(diagnostic) =>
              ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
            case Right(shape) =>
              lower(shape, summon[Context]) match
                case Left(failure) =>
                  ExpansionHelpers.rejected(
                    s"${failure.code}: ${failure.detail}",
                    input.annotatedClass
                  )
                case Right(lowered) =>
                  // This first slice treats any direct raw companion member with
                  // the generated method name as a bounded syntactic conflict.
                  // PreserveExisting keeps that companion exact and adds nothing.
                  ExpansionHelpers.addMethodToCompanion(
                    input,
                    lowered.tree,
                    CompanionMethodConflictPolicy.PreserveExisting
                  )
