package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait MismatchedEnclosingBounds[N <: Nat, M <: Other]:
  type Out <: Nat
