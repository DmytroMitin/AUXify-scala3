package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@apply
@aux
trait ApplyAuxAliasMutualChain[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type First = Second
  type Second = First
