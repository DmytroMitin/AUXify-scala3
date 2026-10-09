package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait CurriedPrivate[A]:
  private def combine(a: A)(b: A): A
