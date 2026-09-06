package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait InfixShow[A]:
  infix def show(a: A): String
