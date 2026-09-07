package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyAlias[N <: Nat, M <: Nat]:
  infix type Out = Nat

object AuxApplyAlias:
  val retained = 41
