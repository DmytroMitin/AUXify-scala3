package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance
import CurriedOutMethodNames.Out

object CurriedOutMethodNames:
  type Out = String

@instance
trait CurriedOutMethod[A]:
  def combine(left: A)(right: A): A
  def identity(value: Out): Out = value
