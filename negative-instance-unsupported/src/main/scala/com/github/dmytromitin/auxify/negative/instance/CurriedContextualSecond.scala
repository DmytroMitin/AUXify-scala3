package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedContextualSecond[A]:
  def combine(a: A)(using b: A): A
