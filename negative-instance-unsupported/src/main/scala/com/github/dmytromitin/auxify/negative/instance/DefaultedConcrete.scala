package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait DefaultedConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def twice(a: A = empty): A = combine(a, a)
