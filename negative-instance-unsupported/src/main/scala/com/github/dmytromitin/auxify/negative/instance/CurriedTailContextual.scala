package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailContextual[A]:
  def combine(a: A)(b: A): A
  def invalid(using value: A): A = value
