package com.github.dmytromitin.auxify.negative.modifiers

import com.github.dmytromitin.auxify.macros.{apply, instance}

@apply
@instance
trait InfixApplyThenInstance[A]:
  infix def empty: A
  def combine(a: A, a1: A): A

@instance
@apply
trait InfixInstanceThenApply[A]:
  infix def empty: A
  def combine(a: A, a1: A): A

@apply
@instance
trait InfixAliasApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix type Item = A

@instance
@apply
trait InfixAliasInstanceThenApply[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix type Item = A

@apply
@instance
trait InfixZeroApplyThenInstance[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def zero: A = empty

@instance
@apply
trait InfixZeroInstanceThenApply[A]:
  def empty: A
  def combine(a: A, a1: A): A
  infix def zero: A = empty
