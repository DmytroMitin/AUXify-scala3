package com.github.dmytromitin.auxify.negative.syntax

import com.github.dmytromitin.auxify.macros.syntax
import SyntaxOutMethodNames.Out

object SyntaxOutMethodNames:
  type Out = String

@syntax
trait SyntaxOutMethod[A]:
  def combine(left: A, right: A): A
  def identity(value: Out): Out = value
