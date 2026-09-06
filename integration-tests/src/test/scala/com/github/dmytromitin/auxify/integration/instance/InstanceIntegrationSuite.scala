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
