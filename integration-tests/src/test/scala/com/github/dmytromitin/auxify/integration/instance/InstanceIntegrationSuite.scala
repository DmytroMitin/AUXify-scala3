package com.github.dmytromitin.auxify.integration.instance

class InstanceIntegrationSuite extends munit.FunSuite:
  test("constructs and runs the canonical Monoid factory") {
    val intAddition: Monoid[Int] =
      Monoid.instance(0, _ + _)

    assertEquals(intAddition.empty, 0)
    assertEquals(intAddition.combine(20, 22), 42)
    assertEquals(Monoid.preserved, 41)
  }

  test("keeps the parameterless carrier by-name") {
    var evaluations = 0
    val observed: Monoid[Int] = Monoid.instance(
      {
        evaluations += 1
        evaluations
      },
      _ + _
    )

    assertEquals(evaluations, 0)
    assertEquals(observed.empty, 1)
    assertEquals(observed.empty, 2)
    assertEquals(evaluations, 2)
  }

  test("constructs a coherently renamed instance factory") {
    val words: Choice[String] =
      Choice.instance("fallback", (left, right) => s"$left/$right")

    assertEquals(words.fallback, "fallback")
    assertEquals(words.select("left", "right"), "left/right")
  }

  test("keeps collision-safe carriers distinct from source names") {
    val values: Collision[Int] =
      Collision.instance(7, _ max _)

    assertEquals(values.emptyValue, 7)
    assertEquals(values.merge(20, 22), 22)
  }

  test("inherits the bounded concrete method and dispatches through combine") {
    var combineCalls = 0
    val derived = DerivedMonoid.instance[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(derived.twice(21), 42)
    assertEquals(combineCalls, 1)
    assertEquals(DerivedMonoid.preserved, 84)
  }

  test("inherits a coherently renamed concrete unary method") {
    val derived = DerivedChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    assertEquals(derived.duplicate("same"), "same/same")
  }

  test("inherits a concrete type alias and preserves by-name factory semantics") {
    var evaluations = 0
    val wrapped = WrappedMonoid.instance[Int](
      {
        evaluations += 1
        0
      },
      _ + _
    )

    val item: wrapped.Item = 42
    val equality: wrapped.Item =:= Int = summon[wrapped.Item =:= Int]
    assertEquals(equality(item), 42)
    assertEquals(evaluations, 0)
    assertEquals(wrapped.empty, 0)
    assertEquals(evaluations, 1)
    assertEquals(wrapped.combine(20, 22), 42)
    assertEquals(WrappedMonoid.preserved, 126)
  }

  test("inherits a coherently renamed concrete type alias family") {
    val wrapped = WrappedChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    val value: wrapped.Value = "typed"
    assertEquals(value, "typed")
    assertEquals(wrapped.fallback, "fallback")
    assertEquals(wrapped.select("left", "right"), "left/right")
  }

  test("preserves a direct existing instance for the alias family") {
    val wrapped = ExistingWrapped.instance[Int](6, _ + _)
    val item: wrapped.Item = 6

    assertEquals(item, 6)
    assertEquals(wrapped.combine(20, 22), 42)
    assertEquals(ExistingWrapped.instanceCalls, 1)
  }
