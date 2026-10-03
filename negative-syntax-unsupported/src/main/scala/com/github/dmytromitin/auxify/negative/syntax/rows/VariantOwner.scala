package com.github.dmytromitin.auxify.negative.syntax.rows

import com.github.dmytromitin.auxify.macros.{syntax as auxSyntax}
import scala.annotation.unchecked.uncheckedVariance

@com.github.dmytromitin.auxify.macros.syntax
trait SyntaxVariantOwner[+A]:
  def combine(a: A @uncheckedVariance, a1: A @uncheckedVariance): A
