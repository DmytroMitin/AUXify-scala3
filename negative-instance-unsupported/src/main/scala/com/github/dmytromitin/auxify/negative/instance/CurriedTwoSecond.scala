package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTwoSecond[A]:
  def combine(a: A)(b: A, x: A): A
