package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyBounded[N <: Nat, M <: Nat]:
  infix type Out <: Nat

object AuxApplyBounded:
  val retained = 41
