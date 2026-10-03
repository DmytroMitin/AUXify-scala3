package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait BinaryZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero(a: A, a1: A): A = combine(a, a1)
