package com.github.dmytromitin.auxify.macros.internal

import dotty.tools.dotc.ast.Trees
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Names.{termName, typeName}

import paradise3.api.{
  ExpansionDiagnostic,
  ExpansionEdit,
  ExpansionHandler,
  ExpansionInput,
  ExpansionOutcome,
  ExpansionTarget
}
import paradise3.api.helpers.ExpansionTransforms

import quasiquotes.definitions.dotty.SelfAbstractTypeMemberPeerBridge

final class SelfHandler extends ExpansionHandler:
  override val annotationName: String =
    "com.github.dmytromitin.auxify.macros.self"

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
    ExpansionEdit.finish:
      for
        prepared <- prepare(input)
        lowered <- lower(
          input.primary.name,
          prepared.selfAliasName,
          summon[Context]
        ).left.map(failure =>
          ExpansionDiagnostic(
            s"${failure.code}: ${failure.detail}",
            input.currentAnnotation.sourcePos
          )
        )
        edit <- ExpansionEdit.start(input)
        updated <- ExpansionTransforms.prepareTraitSelf(
          prepared.self,
          List(lowered.tree)
        )(edit)
      yield updated

  private final case class PreparedSelf(
      selfAliasName: String,
      self: untpd.ValDef
  )

  private def prepare(
      input: ExpansionInput
  )(using Context): Either[ExpansionDiagnostic, PreparedSelf] =
    val requirement =
      "bounded self preparation requires one ordinary non-case, non-sealed trait with zero type parameters and no constructor/value parameters"

    input.primary match
      case ExpansionTarget.Trait(tree) =>
        input.targetView.flatMap: view =>
          val rejection =
            if view.modifiers.isCase then
              Some(
                ExpansionDiagnostic(
                  s"$requirement; case trait `${view.className}` is unsupported",
                  view.classPos
                )
              )
            else if view.modifiers.isSealed then
              Some(
                ExpansionDiagnostic(
                  s"$requirement; sealed trait `${view.className}` is unsupported",
                  view.classPos
                )
              )
            else if view.typeParameters.nonEmpty then
              Some(
                ExpansionDiagnostic(
                  s"$requirement; found ${view.typeParameters.size} type parameters",
                  view.typeParameters.head.pos
                )
              )
            else
              view.constructorClauses.find(_.parameters.nonEmpty).map: clause =>
                ExpansionDiagnostic(
                  s"$requirement; trait constructor/value parameters are unsupported",
                  clause.pos
                )

          rejection match
            case Some(diagnostic) => Left(diagnostic)
            case None =>
              tree.rhs match
                case template: Template =>
                  template.body.collectFirst:
                    case value: TypeDef if value.name == typeName("Self") => value
                  match
                    case Some(conflict) =>
                      Left(
                        ExpansionDiagnostic(
                          s"trait `${input.primary.name}` already contains direct type member `Self`; bounded self preparation requires deterministic rejection",
                          conflict.sourcePos
                        )
                      )
                    case None => prepareSelf(input, tree, template)
                case _ =>
                  Left(
                    ExpansionDiagnostic(
                      s"trait `${input.primary.name}` has an unsupported non-template primary shape for bounded self preparation",
                      input.currentAnnotation.sourcePos
                    )
                  )
      case primary =>
        Left(
          ExpansionDiagnostic(
            s"$requirement; found ${primary.kind.toString.toLowerCase} `${primary.name}`",
            input.currentAnnotation.sourcePos
          )
        )

  private def prepareSelf(
      input: ExpansionInput,
      tree: TypeDef,
      template: Template
  )(using Context): Either[ExpansionDiagnostic, PreparedSelf] =
    val existing = template.self
    if existing.eq(EmptyValDef) then
      val alias = freshSelfAlias(template)
      val generated =
        untpd.ValDef(termName(alias), untpd.TypeTree(), existing.rhs)
          .withMods(Trees.mods(existing))
          .withSpan(tree.span)
          .asInstanceOf[ValDef]
      Right(PreparedSelf(alias, generated))
    else if usableNamedSelf(existing) then
      Right(PreparedSelf(existing.name.toString, existing))
    else
      Left(
        ExpansionDiagnostic(
          s"trait `${input.primary.name}` has an unsupported or malformed raw self declaration for bounded self preparation",
          existing.sourcePos
        )
      )

  private def usableNamedSelf(self: ValDef)(using Context): Boolean =
    val decodedName = self.name.toString
    decodedName.nonEmpty &&
      decodedName != "_" &&
      self.tpt != null &&
      !self.tpt.eq(EmptyTree) &&
      self.rhs != null &&
      self.rhs.eq(EmptyTree)

  private def freshSelfAlias(template: Template)(using Context): String =
    val occupied = template.body.iterator.flatMap:
      case member: ValDef    => Some(member.name.toString)
      case member: DefDef    => Some(member.name.toString)
      case member: ModuleDef => Some(member.name.toString)
      case _                 => None
    .toSet

    Iterator
      .from(0)
      .map(index => if index == 0 then "self" else s"self$$$index")
      .find(candidate => !occupied.contains(candidate))
      .get
