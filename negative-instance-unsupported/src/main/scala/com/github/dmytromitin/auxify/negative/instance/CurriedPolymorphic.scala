package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedPolymorphic[A]:
  def combine[B](a: A)(b: A): A
