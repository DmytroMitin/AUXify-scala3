package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated
import UnaryOutMethodNames.Out

object UnaryOutMethodNames:
  type Out = String

@delegated
trait UnaryOutMethod[A]:
  def show(value: A): String
  def identity(value: Out): Out = value
