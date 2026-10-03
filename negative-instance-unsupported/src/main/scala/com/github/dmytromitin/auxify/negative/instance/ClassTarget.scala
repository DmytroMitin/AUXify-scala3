package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
class ClassTarget[A]:
  def empty: A = ???
  def combine(a: A, a1: A): A = ???
