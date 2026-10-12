package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, delegated}

@delegated
@apply
trait ReverseLateParameterlessDelegatedRejection[A]:
  def empty: A
  type Item = A
  def duplicate(a: A): A = a
  val invalid: A
