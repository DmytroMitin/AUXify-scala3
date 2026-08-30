package com.github.dmytromitin.auxify.integration

import com.github.dmytromitin.auxify.macros.self

@self
trait Nat:
  type Existing = String

@self
trait NamedNat:
  stable =>
  def retainedNamedAlias: stable.type = stable

@self
trait CollisionNat:
  val self: Int = 1
  def self$1: Int = 2
