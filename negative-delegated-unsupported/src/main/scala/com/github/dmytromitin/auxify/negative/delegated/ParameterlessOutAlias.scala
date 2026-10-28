package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated
import ParameterlessOutAliasNames.Out

object ParameterlessOutAliasNames:
  type Out = String

@delegated
trait ParameterlessOutAlias[A]:
  def value: A
  type Copy = Out
