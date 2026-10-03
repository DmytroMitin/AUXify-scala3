package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.{syntax as auxSyntax}

@com.github.dmytromitin.auxify.macros.syntax
trait SyntaxMultipleBodyMembers[A]:
  def combine(a: A, a1: A): A
  def other(a: A, a1: A): A
