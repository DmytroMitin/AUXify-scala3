package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait OverrideZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  override def zero: A = empty
