package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailAliasThenEmptyClause[A]:
  def combine(a: A)(b: A): A
  type Item = A
  def invalid(): A = ???
