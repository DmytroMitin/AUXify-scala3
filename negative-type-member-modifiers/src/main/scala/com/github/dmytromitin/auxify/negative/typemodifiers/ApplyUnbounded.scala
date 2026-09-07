package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
trait ApplyUnbounded[N <: Nat, M <: Nat]:
  infix type Out

object ApplyUnbounded:
  val retained = 41
