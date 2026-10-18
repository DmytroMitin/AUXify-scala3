package com.github.dmytromitin.auxify.negative.typemodifiers

import com.github.dmytromitin.auxify.macros.{apply, aux}

@aux
@apply
trait AuxApplyAliasMissingAfterValidChain[N <: Nat, M <: Nat]:
  type Out <: Nat
  type Copy = Out
  type CopyAgain = Copy
  type Broken = Missing
