package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, delegated}

@apply
@delegated
trait LateParameterlessDelegatedRejection[A]:
  def empty: A
  type Item = A
  def duplicate(a: A): A = a
  val invalid: A
