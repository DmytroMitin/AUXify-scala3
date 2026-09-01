package com.github.dmytromitin.auxify.negative.composition

import com.github.dmytromitin.auxify.macros.{apply, delegated}

@apply
@delegated
trait LateDelegatedRejection[A]:
  def show(a: A): List[String]
