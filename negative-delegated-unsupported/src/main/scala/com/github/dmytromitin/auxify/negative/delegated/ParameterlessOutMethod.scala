package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated
import ParameterlessOutMethodNames.Out

object ParameterlessOutMethodNames:
  type Out = String

@delegated
trait ParameterlessOutMethod[A]:
  def value: A
  def identity(value: Out): Out = value
