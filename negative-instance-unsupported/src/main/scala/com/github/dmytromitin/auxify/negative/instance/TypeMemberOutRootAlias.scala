package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberOutRootAlias[A]:
  type Out
  type Item = String
