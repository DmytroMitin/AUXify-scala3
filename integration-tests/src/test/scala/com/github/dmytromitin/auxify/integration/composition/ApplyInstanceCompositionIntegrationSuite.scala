package com.github.dmytromitin.auxify.integration.composition

class ApplyInstanceCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then instance exposes both generated methods and preserves by-name semantics") {
    var evaluations = 0
    val constructed = ApplyThenInstance.instance(
      {
        evaluations += 1
        0
      },
      _ + _
    )
    given ApplyThenInstance[Int] = constructed

    assertEquals(evaluations, 0)
    assert(ApplyThenInstance[Int].eq(constructed))
    assertEquals(constructed.empty, 0)
    assertEquals(evaluations, 1)
    assertEquals(constructed.combine(20, 22), 42)
    assertEquals(ApplyThenInstance.preservedBefore, 41)
    assertEquals(ApplyThenInstance.preservedAfter, 43)
  }

  test("instance then apply derives the renamed family and preserves its companion") {
    val constructed = InstanceThenApply.instance(
      "fallback",
      (left, right) => s"$left/$right"
    )
    given InstanceThenApply[String] = constructed

    assert(InstanceThenApply[String].eq(constructed))
    assertEquals(constructed.fallback, "fallback")
    assertEquals(constructed.select("left", "right"), "left/right")
    assertEquals(InstanceThenApply.preserved, 84)
  }

  test("an existing apply is preserved while instance is generated") {
    val constructed = ExistingApplyThenInstance.instance(0, _ + _)
    given ExistingApplyThenInstance[Int] = constructed

    assert(ExistingApplyThenInstance[Int].eq(constructed))
    assertEquals(ExistingApplyThenInstance.applyCalls, 1)
    assertEquals(constructed.combine(20, 22), 42)
    assertEquals(ExistingApplyThenInstance.retained, 7)
  }

  test("an existing instance is preserved while apply is generated") {
    given ExistingInstanceThenApply[Int] =
      ExistingInstanceThenApply.instance(6, _ + _)

    val selected = ExistingInstanceThenApply[Int]
    assertEquals(selected.empty, 6)
    assertEquals(selected.combine(20, 22), 42)
    assertEquals(ExistingInstanceThenApply.instanceCalls, 1)
    assertEquals(ExistingInstanceThenApply.retained, 11)
  }

  test("existing apply and instance methods remain independent") {
    given ExistingBoth[Int] = ExistingBoth.instance(9, _ + _)

    val selected = ExistingBoth[Int]
    assertEquals(selected.empty, 9)
    assertEquals(selected.combine(20, 22), 42)
    assertEquals(ExistingBoth.applyCalls, 1)
    assertEquals(ExistingBoth.instanceCalls, 1)
    assertEquals(ExistingBoth.retained, 13)
  }
