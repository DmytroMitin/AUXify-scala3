package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.instance

@instance
trait InfixConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def twice(a: A): A = combine(a, a)
