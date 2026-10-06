package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberAnnotated[A]:
  @deprecated("unsupported", "")
  type Out
