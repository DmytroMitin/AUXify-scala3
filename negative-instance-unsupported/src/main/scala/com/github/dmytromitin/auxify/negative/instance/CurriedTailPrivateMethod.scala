package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailPrivateMethod[A]:
  def combine(a: A)(b: A): A
  private def invalid(value: A): A = value
