package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, instance}

@instance
@apply
trait AbstractTypeSecondMember[A]:
  type Out
  type Other
