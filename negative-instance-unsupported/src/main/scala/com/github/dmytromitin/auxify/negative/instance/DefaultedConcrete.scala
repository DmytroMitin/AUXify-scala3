package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait DefaultedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold5(a: A, b: A, c: A, d: A = empty, e: A): A = a
