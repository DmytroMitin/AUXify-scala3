package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance, syntax}

@apply
@instance
@syntax
trait CurriedThirdParticipant[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def twice(value: A): A = combine(value)(value)
