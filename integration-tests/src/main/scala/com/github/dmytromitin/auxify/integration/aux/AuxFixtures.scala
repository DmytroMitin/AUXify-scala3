package com.github.dmytromitin.auxify.integration.aux

import com.github.dmytromitin.auxify.macros.aux

trait Nat
final class Zero extends Nat
final class One extends Nat
final class Two extends Nat

@aux
trait Add[N <: Nat, M <: Nat]:
  type Out <: Nat
  def apply(n: N, m: M): Out
  def description: String

object Add:
  given Add[Zero, One] with
    type Out = One
    def apply(n: Zero, m: One): One = m
    def description: String = "zero-plus-one"

trait Natural
final class LeftValue extends Natural
final class RightValue extends Natural
final class ResultValue extends Natural

@aux
trait Combine[Left <: Natural, Right <: Natural]:
  type Result <: Natural

object Combine:
  given Combine[LeftValue, RightValue] with
    type Result = ResultValue

@aux
trait ExistingCompanion[N <: Nat, M <: Nat]:
  type Out <: Nat

object ExistingCompanion:
  val before = 41
  object Nested
  val after = 43
  given ExistingCompanion[Zero, One] with
    type Out = One

@aux
trait ExistingAuxType[N <: Nat, M <: Nat]:
  type Out <: Nat

object ExistingAuxType:
  type Aux = String
  val retained = 7

@aux
trait TermNamespace[N <: Nat, M <: Nat]:
  type Out <: Nat

object TermNamespace:
  val Aux = 9
  given TermNamespace[Zero, One] with
    type Out = One
