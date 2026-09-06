package com.github.dmytromitin.auxify.integration.modifieraudit

import com.github.dmytromitin.auxify.macros.{apply, delegated, instance}

// Safety characterizations only: these modifier-bearing sources are outside the
// documented supported contract until Macro-Paradise exposes normalized flags.
@instance
trait CurrentlyAdmittedInfixInstance[A]:
  infix def empty: A
  infix def combine(a: A, a1: A): A
  infix def twice(a: A): A = combine(a, a)

@delegated
trait CurrentlyAdmittedInfixDelegated[A]:
  infix def show(a: A): String

object CurrentlyAdmittedInfixDelegated:
  given CurrentlyAdmittedInfixDelegated[Int] with
    infix def show(a: Int): String = a.toString

@apply
@instance
trait CurrentlyAdmittedInfixApplyThenInstance[A]:
  infix def empty: A
  def combine(a: A, a1: A): A

@instance
@apply
trait CurrentlyAdmittedInfixInstanceThenApply[A]:
  infix def empty: A
  def combine(a: A, a1: A): A
