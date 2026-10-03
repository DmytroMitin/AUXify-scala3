package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.instance

@instance
trait InfixZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def zero: A = empty
