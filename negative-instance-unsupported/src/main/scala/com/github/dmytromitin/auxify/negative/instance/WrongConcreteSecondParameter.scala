package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongConcreteMiddleParameter[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold5(a: A, b: A, c: Other, d: A, e: A): A = a
