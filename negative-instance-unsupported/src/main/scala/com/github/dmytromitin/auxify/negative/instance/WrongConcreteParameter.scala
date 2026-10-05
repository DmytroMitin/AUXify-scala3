package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongConcreteEarlyParameter[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def fold5(a: Other, b: A, c: A, d: A, e: A): A = b
