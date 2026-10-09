package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedWrongFirst[A]:
  def combine(a: String)(b: A): A
