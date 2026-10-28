package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberOutLateInvalid[A]:
  type Out
  type Copy = Out
  type Again = Copy
  def identity(value: Again): Out = value
  def invalid(): Again = ???
