package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.core.Contexts.Context

import paradise3.api.{
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  ParadiseAnnotationExpander
}
import paradise3.api.helpers.ExpansionHelpers

import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

final class SelfHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.self"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.PlainZeroParameterTrait

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    SelfHandler.expandWithLowering(input): (traitName, selfAliasName, context) =>
      SelfDefinitionBuilder.lower(traitName, selfAliasName)(using context)

private[internal] object SelfHandler:
  type Lowering = (
      String,
      String,
      Context
  ) => Either[
    SelfAbstractTypeMemberPeerBridge.Failure,
    SelfAbstractTypeMemberPeerBridge.Lowered
  ]

  def expandWithLowering(
      input: ExpansionInput
  )(
      lower: Lowering
  )(using Context): ExpansionOutcome =
    var loweringFailure: Option[SelfAbstractTypeMemberPeerBridge.Failure] = None
    val prepared =
      ExpansionHelpers.addPreparedSelfTypeToTrait(input): preparation =>
        lower(input.className, preparation.selfAliasName, summon[Context]) match
            case Right(lowered) => lowered.tree
            case Left(failure) =>
              loweringFailure = Some(failure)
              null

    loweringFailure match
      case Some(failure) =>
        ExpansionHelpers.rejected(
          s"${failure.code}: ${failure.detail}",
          input.annotatedClass
        )
      case None => prepared
