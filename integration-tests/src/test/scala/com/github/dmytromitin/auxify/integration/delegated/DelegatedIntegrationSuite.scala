package com.github.dmytromitin.auxify.integration.delegated

class DelegatedIntegrationSuite extends munit.FunSuite:
  test("canonical public marker forwards an ordinary companion invocation") {
    assertEquals(Show.show(42), "42")
    assertEquals(Show.preservedBefore, 41)
    assertEquals(Show.preservedAfter, 43)
  }

  test("renamed trait method parameter and result names are structurally derived") {
    assertEquals(Render.render(17L), Text("rendered:17"))
  }

  test("an existing direct same-name method is preserved without duplication") {
    assertEquals(Existing.describe(9), "preserved:9")
    assertEquals(Existing.calls, 1)
  }
