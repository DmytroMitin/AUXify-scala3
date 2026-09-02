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
  CompanionTypeConflictPolicy,
  ExpansionHelpers
}

import quasiquotes.definitions.dotty.AuxTypeAliasPeerBridge

final class AuxHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.aux"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.TwoUpperBoundedGenericTrait

  override val compositionPolicy: ExpansionCompositionPolicy =
    ExpansionCompositionPolicy.SourceOrdered

  override val consumesExistingCompanion: Boolean = true

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
    input.annotatedClassTypeStructureView match
      case Left(diagnostic) =>
        ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
      case Right(structure) =>
        AuxSourceShapeDecoder.decode(input.className, structure) match
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
                ExpansionHelpers.addTypeToCompanion(
                  input,
                  lowered.tree,
                  CompanionTypeConflictPolicy.PreserveExisting
                )
