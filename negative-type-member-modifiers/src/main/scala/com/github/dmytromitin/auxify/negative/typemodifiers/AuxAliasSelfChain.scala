package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.aux

@aux
trait AuxAliasSelfChain[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Copy
