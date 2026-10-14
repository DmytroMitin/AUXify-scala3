package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, syntax}

@apply
@syntax
trait LateSyntaxRejection[A]:
  def combine(left: A, right: A): A
  type Item = A
  def twice(value: A): A = combine(value, value)
  type Value = A
  val invalid: A = ???
