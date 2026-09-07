package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxAlias[N <: Nat, M <: Nat]:
  infix type Out = Nat

object ApplyAuxAlias:
  val retained = 41
