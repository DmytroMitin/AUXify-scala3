package com.github.dmytromitin.auxify.integration.instance

import com.github.dmytromitin.auxify.macros.instance

@instance
trait Monoid[A]:
  def empty: A
  def combine(a: A, a1: A): A

object Monoid:
  val preserved = 41

@instance
trait Choice[Element]:
  def fallback: Element
  def select(left: Element, right: Element): Element

@instance
trait Collision[Element]:
  def emptyValue: Element
  def merge(combineFunction: Element, right: Element): Element
