package com.github.dmytromitin.auxify.integration

class ApplyIntegrationSuite extends munit.FunSuite:
  test("creates a companion with an apply method") {
    val createdInstance = new Created[String] {}

    assert(Created[String](using createdInstance) eq createdInstance)
  }

  test("merges apply after preserving existing companion members") {
    assertEquals(Show[String].show("abc"), "abc")
    assertEquals(Show.preservedBefore, 41)
    assertEquals(Show.Nested.apply(1), 2)
    assertEquals(Show.applyLike(1), 3)
    assertEquals(Show.preservedAfter, 43)
  }

  test("derives the trait and generic type-parameter names") {
    val evidenceInstance = new Evidence[Int] {}

    assert(Evidence[Int](using evidenceInstance) eq evidenceInstance)
  }

  test("preserves an existing direct apply method without duplication") {
    val existingInstance = new ExistingApply[String] {}

    assert(ExistingApply[String](using existingInstance) eq existingInstance)
    assertEquals(ExistingApply.calls, 1)
  }
