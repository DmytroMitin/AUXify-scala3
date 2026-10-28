package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance
import HistoricalOutAliasNames.Out

object HistoricalOutAliasNames:
  type Out = String

@instance
trait HistoricalOutAlias[A]:
  def empty: A
  def combine(left: A, right: A): A
  type Copy = Out
