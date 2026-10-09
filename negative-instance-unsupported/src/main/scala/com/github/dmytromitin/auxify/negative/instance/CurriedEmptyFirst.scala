package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedEmptyFirst[A]:
  def combine()(b: A): A
