package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.instance

@instance
trait InfixCombine[A]:
  def empty: A
  infix def combine(a: A, a1: A): A
