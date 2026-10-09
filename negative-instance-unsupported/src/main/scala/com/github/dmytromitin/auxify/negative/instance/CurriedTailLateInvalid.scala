package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailLateInvalid[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def twice(a: A): A = combine(a)(a)
  type Value = A
  def invalid(a: A)(b: A): A = combine(a)(b)
