package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.syntax

@syntax
trait SyntaxPolymorphicTail[A]:
  def combine(a: A, b: A): A
  def other[B](a: A): A = a
