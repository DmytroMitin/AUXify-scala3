package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedTailAdditionalAbstractMethod[A]:
  def combine(a: A)(b: A): A
  def other(value: A): A
