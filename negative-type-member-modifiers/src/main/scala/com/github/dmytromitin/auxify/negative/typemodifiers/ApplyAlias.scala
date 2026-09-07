package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
trait ApplyAlias[N <: Nat, M <: Nat]:
  infix type Out = Nat

object ApplyAlias:
  val retained = 41
