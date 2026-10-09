package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailPolymorphic[A]:
  def combine(a: A)(b: A): A
  def invalid[B](value: A): A = value
