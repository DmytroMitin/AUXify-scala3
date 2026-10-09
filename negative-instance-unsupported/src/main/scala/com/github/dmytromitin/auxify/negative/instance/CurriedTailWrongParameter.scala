package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailWrongParameter[A]:
  def combine(a: A)(b: A): A
  def invalid(value: String): A = ???
