package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait TypeMemberOutForward[A]:
  type Out
  type Copy = Later
  type Later = Out
