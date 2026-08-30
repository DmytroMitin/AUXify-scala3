package com.github.dmytromitin.auxify.negative

import com.github.dmytromitin.auxify.macros.self

@self
trait ExistingSelfConflict:
  type Self = String
