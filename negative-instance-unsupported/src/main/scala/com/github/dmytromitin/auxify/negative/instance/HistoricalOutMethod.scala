package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance
import HistoricalOutMethodNames.Out

object HistoricalOutMethodNames:
  type Out = String

@instance
trait HistoricalOutMethod[A]:
  def empty: A
  def combine(left: A, right: A): A
  def identity(value: Out): Out = value
