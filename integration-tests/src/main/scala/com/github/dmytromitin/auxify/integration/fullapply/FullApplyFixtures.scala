package com.github.dmytromitin.auxify.integration.fullapply

import com.github.dmytromitin.auxify.macros.apply

trait Nat
final class Zero extends Nat
final class One extends Nat

@apply
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out

object Add:
  val preservedBefore = 71
  object Nested:
    def apply(value: Int): Int = value + 1
  def applyLike(value: Int): Int = value + 2
  given zeroPlusOne: Add[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m
  val preservedAfter = 73

def refinedAdd[N <: Nat, M <: Nat](using inst: Add[N, M]):
    Add[N, M] { type Out = inst.Out } =
  Add[N, M]

trait Natural
final class LeftNatural extends Natural
final class RightNatural extends Natural

@apply
trait Combine[Left <: Natural, Right <: Natural]:
  type Result <: Natural
  def combine(left: Left, right: Right): Result

object Combine:
  given leftRight: Combine[LeftNatural, RightNatural] with
    type Result = RightNatural
    def combine(left: LeftNatural, right: RightNatural): RightNatural = right

def refinedCombine[Left <: Natural, Right <: Natural](using
    inst: Combine[Left, Right]
): Combine[Left, Right] { type Result = inst.Result } =
  Combine[Left, Right]

@apply
trait ExistingFullApply[N <: Nat, M <: Nat]:
  type Out <: Nat

object ExistingFullApply:
  var calls = 0
  def apply[N <: Nat, M <: Nat](using
      inst: ExistingFullApply[N, M]
  ): ExistingFullApply[N, M] =
    calls += 1
    inst
