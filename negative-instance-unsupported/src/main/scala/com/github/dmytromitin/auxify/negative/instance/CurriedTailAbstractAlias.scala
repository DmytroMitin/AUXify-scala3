package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailAbstractAlias[A]:
  def combine(a: A)(b: A): A
  type Item
