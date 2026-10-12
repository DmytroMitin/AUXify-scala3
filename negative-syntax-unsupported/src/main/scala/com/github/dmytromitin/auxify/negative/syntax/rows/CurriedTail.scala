package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxCurriedTail[A]:
  def combine(a: A, b: A): A
  def other(a: A)(b: A): A = a
