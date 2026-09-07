package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.instance

@instance
trait InfixEmpty[A]:
  infix def empty: A
  def combine(a: A, a1: A): A

@instance
trait InfixCombine[A]:
  def empty: A
  infix def combine(a: A, a1: A): A

@instance
trait InfixConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def twice(a: A): A = combine(a, a)

@instance
trait InfixZero[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def zero: A = empty
