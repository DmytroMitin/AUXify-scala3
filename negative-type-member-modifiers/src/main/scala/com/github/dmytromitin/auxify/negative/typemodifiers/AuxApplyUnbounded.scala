package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyUnbounded[N <: Nat, M <: Nat]:
  infix type Out

object AuxApplyUnbounded:
  val retained = 41
