package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailAnnotatedMethod[A]:
  def combine(a: A)(b: A): A
  @deprecated def invalid(value: A): A = value
