package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Names.*

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTargetProfile,
  ParadiseAnnotationExpander
}
import paradise3.api.helpers.ExpansionHelpers

final class ApplyHandler extends ParadiseAnnotationExpander:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.apply"

  override val targetProfile: ExpansionTargetProfile =
    ExpansionTargetProfile.RestrictedGenericTraitApply

  override val consumesExistingCompanion: Boolean = true

  override def expand(input: ExpansionInput)(using Context): ExpansionOutcome =
    ExpansionHelpers.withAnnotatedClassView(input): view =>
      val typeParameterName = view.typeParameters.head.name
      ApplyDefinitionBuilder.lower(input.className, typeParameterName) match
        case Left(failure) =>
          ExpansionOutcome.Rejected(
            List(
              ExpansionDiagnostic(
                s"${failure.code}: ${failure.detail}",
                input.annotatedClass.sourcePos
              )
            ),
            input.annotatedClass
          )
        case Right(lowered) =>
          val primary = stripCurrentAnnotation(input)
          val companion =
            input.existingCompanion match
              case Some(existing) => mergeApply(existing, lowered.tree)
              case None => freshCompanion(input, List(lowered.tree))
          ExpansionHelpers.structured(primary, Some(companion))

  private def stripCurrentAnnotation(
      input: ExpansionInput
  )(using Context): TypeDef =
    val currentMods = Trees.mods(input.annotatedClass)
    val preserved =
      input.currentAnnotation match
        case Some(current) => currentMods.annotations.filterNot(_ eq current)
        case None => Nil
    input.annotatedClass
      .withMods(currentMods.withAnnotations(preserved))
      .asInstanceOf[TypeDef]

  private def mergeApply(
      existing: ModuleDef,
      generatedApply: DefDef
  )(using Context): ModuleDef =
    val template = existing.impl
    val existingBody = template.body(using summon[Context])
    val mergedBody =
      if existingBody.exists(directMemberNamedApply) then existingBody
      else existingBody :+ generatedApply
    val mergedTemplate =
      untpd.cpy.Template(template)(
        template.constr,
        template.parentsOrDerived(using summon[Context]),
        template.derived,
        template.self,
        mergedBody
      )
    untpd.cpy.ModuleDef(existing)(existing.name, mergedTemplate)

  private def directMemberNamedApply(tree: untpd.Tree): Boolean =
    tree match
      case member: MemberDef => member.name.toString == "apply"
      case _ => false

  private def freshCompanion(
      input: ExpansionInput,
      body: List[untpd.Tree]
  )(using Context): ModuleDef =
    given dotty.tools.dotc.util.SourceFile = input.annotatedClass.source
    ModuleDef(
      termName(input.className),
      Template(emptyConstructor, Nil, Nil, EmptyValDef, body)
    )
