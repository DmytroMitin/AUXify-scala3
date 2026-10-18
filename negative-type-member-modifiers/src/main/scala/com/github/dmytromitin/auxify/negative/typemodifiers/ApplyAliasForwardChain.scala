package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.apply

@apply
trait ApplyAliasForwardChain[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type Before = After
  type After = Out
