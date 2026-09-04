package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  ExpansionCompositionPolicy,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  ParadiseAnnotationExpander
}
import paradise3.api.helpers.{
  CompanionMethodConflictPolicy,
  ExpansionHelpers
}

import quasiquotes.definitions.dotty.InstanceFactoryPeerBridge

final class InstanceHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.instance"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.RestrictedGenericTraitApply

  override val compositionPolicy: ExpansionCompositionPolicy =
    ExpansionCompositionPolicy.SourceOrdered

  override val consumesExistingCompanion: Boolean = true

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
    input.annotatedClassView match
      case Left(diagnostic) =>
        ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
      case Right(classView) =>
        input.annotatedClassBodyView match
          case Left(diagnostic) =>
            ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
          case Right(bodyView) =>
            InstanceSourceShapeDecoder.decode(classView, bodyView) match
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
                    ExpansionHelpers.addMethodToCompanion(
                      input,
                      lowered.tree,
                      CompanionMethodConflictPolicy.PreserveExisting
                    )
