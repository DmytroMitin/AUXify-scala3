package com.github.dmytromitin.auxify.negative.syntax

import com.github.dmytromitin.auxify.macros.syntax
import SyntaxOutAliasNames.Out

object SyntaxOutAliasNames:
  type Out = String

@syntax
trait SyntaxOutAlias[A]:
  def combine(left: A, right: A): A
  type Copy = Out
