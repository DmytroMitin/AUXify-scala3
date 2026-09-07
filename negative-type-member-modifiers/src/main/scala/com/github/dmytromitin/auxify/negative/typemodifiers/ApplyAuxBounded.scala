package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxBounded[N <: Nat, M <: Nat]:
  infix type Out <: Nat

object ApplyAuxBounded:
  val retained = 41
