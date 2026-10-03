package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongArity[A]:
  def empty: A
  def combine(a: A): A
