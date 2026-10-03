package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait ProtectedZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  protected def zero: A = empty
