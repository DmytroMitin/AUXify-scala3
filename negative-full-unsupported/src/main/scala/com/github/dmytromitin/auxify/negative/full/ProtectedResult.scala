package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait ProtectedResult[N <: Nat, M <: Nat]:
  protected type Out <: Nat
