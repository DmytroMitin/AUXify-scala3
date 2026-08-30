package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait MultipleResults[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Extra <: Nat
