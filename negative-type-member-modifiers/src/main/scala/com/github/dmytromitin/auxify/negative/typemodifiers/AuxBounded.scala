package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
trait AuxBounded[N <: Nat, M <: Nat]:
  infix type Out <: Nat

object AuxBounded:
  val retained = 41
