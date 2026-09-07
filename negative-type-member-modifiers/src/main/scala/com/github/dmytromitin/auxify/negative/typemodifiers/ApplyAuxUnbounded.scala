package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxUnbounded[N <: Nat, M <: Nat]:
  infix type Out

object ApplyAuxUnbounded:
  val retained = 41
