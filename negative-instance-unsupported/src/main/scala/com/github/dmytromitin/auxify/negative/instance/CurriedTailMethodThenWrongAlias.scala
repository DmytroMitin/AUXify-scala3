package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailMethodThenWrongAlias[A]:
  def combine(a: A)(b: A): A
  def twice(a: A): A = combine(a)(a)
  type Item = String
