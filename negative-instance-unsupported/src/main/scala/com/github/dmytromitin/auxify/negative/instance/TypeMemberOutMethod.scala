package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberOutMethod[A]:
  type Out
  def identity(value: Out): Out = value
