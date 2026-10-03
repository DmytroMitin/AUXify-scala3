package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait AbstractZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def zero: A
