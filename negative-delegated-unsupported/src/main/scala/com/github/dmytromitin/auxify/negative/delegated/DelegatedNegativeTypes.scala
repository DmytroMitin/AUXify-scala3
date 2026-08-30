package com.github.dmytromitin.auxify.negative.delegated

import com.github.dmytromitin.auxify.macros.delegated

@delegated
trait ConcreteDelegated[A]:
  def show(a: A): String = a.toString

@delegated
trait AppliedResultDelegated[A]:
  def show(a: A): List[String]

@delegated
trait PolymorphicDelegated[A]:
  def show[B](a: A): String

@delegated
trait WrongTopologyDelegated[A]:
  def show(a: A, other: A): String
