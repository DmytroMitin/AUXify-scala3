package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
trait AuxAlias[N <: Nat, M <: Nat]:
  infix type Out = Nat

object AuxAlias:
  val retained = 41
