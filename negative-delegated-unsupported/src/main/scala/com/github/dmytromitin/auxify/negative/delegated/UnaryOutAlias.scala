package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated
import UnaryOutAliasNames.Out

object UnaryOutAliasNames:
  type Out = String

@delegated
trait UnaryOutAlias[A]:
  def show(value: A): String
  type Copy = Out
