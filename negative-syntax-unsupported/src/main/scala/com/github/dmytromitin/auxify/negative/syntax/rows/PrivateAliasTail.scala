package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxPrivateAliasTail[A]:
  def combine(a: A, b: A): A
  private type Item = A
