package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait PolyEmpty[A]:
  def empty[B]: A
  def combine(a: A, a1: A): A
