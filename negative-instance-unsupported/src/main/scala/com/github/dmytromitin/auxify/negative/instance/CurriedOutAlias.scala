package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance
import CurriedOutAliasNames.Out

object CurriedOutAliasNames:
  type Out = String

@instance
trait CurriedOutAlias[A]:
  def combine(left: A)(right: A): A
  type Copy = Out
