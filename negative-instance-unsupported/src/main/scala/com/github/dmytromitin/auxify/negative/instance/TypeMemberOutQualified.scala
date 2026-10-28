package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberOutQualified[A]:
  type Out
  type Copy = scala.Predef.String
