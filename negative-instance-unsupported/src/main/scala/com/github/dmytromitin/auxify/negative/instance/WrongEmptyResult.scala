package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait WrongEmptyResult[A]:
  def empty: Other
  def combine(a: A, a1: A): A
