package com.github.dmytromitin.auxify.negative.instance

import com.github.dmytromitin.auxify.macros.instance

trait TypeMemberEvidence[A]

@instance
trait TypeMemberContextBound[A: TypeMemberEvidence]:
  type Out
