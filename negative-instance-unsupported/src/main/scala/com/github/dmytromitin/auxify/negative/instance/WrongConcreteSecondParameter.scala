package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongConcreteSecondParameter[A]:
  def empty: A
  def combine(a: A, a1: A): A
  def combineAgain(a: A, a1: Other): A = a
