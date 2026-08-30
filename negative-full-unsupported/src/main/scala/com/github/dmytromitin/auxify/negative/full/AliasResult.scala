package com.github.dmytromitin.auxify.negative.full

import com.github.dmytromitin.auxify.macros.apply

@apply
trait AliasResult[N <: Nat, M <: Nat]:
  type Out = Nat
