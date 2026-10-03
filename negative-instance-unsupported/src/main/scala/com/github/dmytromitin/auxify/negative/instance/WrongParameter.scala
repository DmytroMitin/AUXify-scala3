package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongParameter[A]:
  def empty: A
  def combine(a: Other, a1: A): A
