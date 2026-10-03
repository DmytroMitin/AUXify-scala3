package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait ImplicitConcrete[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(implicit a: A, a1: A): A = combine(a, a1)
