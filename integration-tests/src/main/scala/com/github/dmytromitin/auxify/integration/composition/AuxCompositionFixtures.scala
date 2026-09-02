package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, aux}

trait AuxNat
final class AuxZero extends AuxNat
final class AuxOne extends AuxNat

@apply
@aux
trait ApplyThenAux[N <: AuxNat, M <: AuxNat]:
  type Out <: AuxNat
  def apply(n: N, m: M): Out

object ApplyThenAux:
  val preserved = 41
  given ApplyThenAux[AuxZero, AuxOne] with
    type Out = AuxOne
    def apply(n: AuxZero, m: AuxOne): AuxOne = m

@aux
@apply
trait AuxThenApply[N <: AuxNat, M <: AuxNat]:
  type Out <: AuxNat
  def apply(n: N, m: M): Out

object AuxThenApply:
  val preserved = 84
  given AuxThenApply[AuxZero, AuxOne] with
    type Out = AuxOne
    def apply(n: AuxZero, m: AuxOne): AuxOne = m

@apply
@aux
trait ExistingApplyThenAux[N <: AuxNat, M <: AuxNat]:
  type Out <: AuxNat

object ExistingApplyThenAux:
  var applyCalls = 0
  def apply[N <: AuxNat, M <: AuxNat](using
      instance: ExistingApplyThenAux[N, M]
  ): ExistingApplyThenAux[N, M] =
    applyCalls += 1
    instance
  given ExistingApplyThenAux[AuxZero, AuxOne] with
    type Out = AuxOne

@aux
@apply
trait ExistingAuxThenApply[N <: AuxNat, M <: AuxNat]:
  type Out <: AuxNat

object ExistingAuxThenApply:
  type Aux = String
  val retained = 7
  given ExistingAuxThenApply[AuxZero, AuxOne] with
    type Out = AuxOne
