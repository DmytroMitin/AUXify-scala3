package com.github.dmytromitin.auxify.negative.aux

import com.github.dmytromitin.auxify.macros.aux

@aux
trait AliasResult[N <: Nat, M <: Nat]:
  type Out = Nat
