package com.github.dmytromitin.auxify.integration.composition

import com.github.dmytromitin.auxify.macros.{apply, syntax}

@apply
@syntax
trait ApplyThenSyntaxNoTail[A]:
  def combine(left: A, right: A): A

object ApplyThenSyntaxNoTail:
  given ApplyThenSyntaxNoTail[Int] with
    def combine(left: Int, right: Int): Int = left + right

@syntax
@apply
trait SyntaxThenApplyNoTail[Element]:
  def merge(left: Element, right: Element): Element

object SyntaxThenApplyNoTail:
  given SyntaxThenApplyNoTail[Int] with
    def merge(left: Int, right: Int): Int = left + right
