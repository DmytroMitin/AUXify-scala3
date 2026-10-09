package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailNestedTrait[A]:
  def combine(a: A)(b: A): A
  trait Nested
