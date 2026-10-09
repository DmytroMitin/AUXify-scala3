package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTwoFirst[A]:
  def combine(a: A, x: A)(b: A): A
