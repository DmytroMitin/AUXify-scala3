package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
trait ApplyBounded[N <: Nat, M <: Nat]:
  infix type Out <: Nat

object ApplyBounded:
  val retained = 41
