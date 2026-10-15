package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait CurriedApplyThenInstanceLateInvalid[A]:
  def combine(a: A)(b: A): A
  def twice(value: A): A = combine(value)(value)
  trait Nested
