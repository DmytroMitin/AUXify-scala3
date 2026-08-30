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

import quasiquotes.definitions.dotty.ContextualMethodPeerBridge

final class ApplyHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.apply"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.RestrictedOrTwoUpperBoundedGenericTrait

  override val consumesExistingCompanion: Boolean = true

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    ExpansionHelpers.withAnnotatedClassView(input): view =>
      view.typeParameters match
        case List(typeParameter) =>
          lowerAndPlace(
            input,
            ApplyDefinitionBuilder.lower(input.className, typeParameter.name)
          )
        case List(_, _) =>
          input.annotatedClassTypeStructureView match
            case Left(diagnostic) =>
              ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
            case Right(structure) =>
              ApplyFullShapeDecoder.decode(input.className, structure) match
                case Left(diagnostic) =>
                  ExpansionHelpers.rejected(diagnostic, input.annotatedClass)
                case Right(shape) =>
                  lowerAndPlace(input, ApplyDefinitionBuilder.lowerFull(shape))
        case _ =>
          ExpansionHelpers.rejected(
            s"unsupported @apply source shape for `${input.className}`",
            input.annotatedClass
          )

  private def lowerAndPlace(
      input: ExpansionInput,
      lowered: Either[
        ContextualMethodPeerBridge.Failure,
        ContextualMethodPeerBridge.Lowered
      ]
  )(using Context): ExpansionOutcome =
    lowered match
      case Left(failure) =>
        ExpansionHelpers.rejected(
          s"${failure.code}: ${failure.detail}",
          input.annotatedClass
        )
      case Right(value) =>
        ExpansionHelpers.addMethodToCompanion(
          input,
          value.tree,
          CompanionMethodConflictPolicy.PreserveExisting
        )
