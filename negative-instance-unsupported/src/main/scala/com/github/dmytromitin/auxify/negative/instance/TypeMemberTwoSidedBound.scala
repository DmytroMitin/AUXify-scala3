package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberTwoSidedBound[A]:
  type Out >: Nothing <: Any
