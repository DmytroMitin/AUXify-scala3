package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.{syntax as auxSyntax}

@com.github.dmytromitin.auxify.macros.syntax
trait SyntaxNoOwnerParameter:
  def combine(a: Int, a1: Int): Int
