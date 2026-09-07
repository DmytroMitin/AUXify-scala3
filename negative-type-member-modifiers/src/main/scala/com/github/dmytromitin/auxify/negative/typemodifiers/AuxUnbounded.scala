package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
trait AuxUnbounded[N <: Nat, M <: Nat]:
  infix type Out

object AuxUnbounded:
  val retained = 41
