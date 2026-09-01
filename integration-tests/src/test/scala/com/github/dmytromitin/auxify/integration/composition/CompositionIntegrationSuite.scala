package com.github.dmytromitin.auxify.integration.composition

class CompositionIntegrationSuite extends munit.FunSuite:
  test("apply then delegated generates both methods and preserves the companion") {
    val instance = ApplyThenDelegated[Int]

    assertEquals(instance.show(7), "apply-first:7")
    assertEquals(ApplyThenDelegated.show(7), "apply-first:7")
    assertEquals(ApplyThenDelegated.preservedBefore, 41)
    assertEquals(ApplyThenDelegated.preservedAfter, 43)
  }

  test("delegated then apply generates both methods and preserves the companion") {
    val instance = DelegatedThenApply[String]

    assertEquals(instance.show("value"), "delegated-first:value")
    assertEquals(DelegatedThenApply.show("value"), "delegated-first:value")
    assertEquals(DelegatedThenApply.preservedBefore, 81)
    assertEquals(DelegatedThenApply.preservedAfter, 85)
  }

  test("an existing apply is preserved while delegated forwarding is generated") {
    val instance = ExistingApplyThenDelegated[Int]

    assertEquals(instance.show(9), "generated-show:9")
    assertEquals(ExistingApplyThenDelegated.show(9), "generated-show:9")
    assertEquals(ExistingApplyThenDelegated.applyCalls, 1)
  }

  test("an existing forwarding method is preserved while apply is generated") {
    val instance = ExistingForwardThenApply[Int]

    assertEquals(instance.show(11), "generated-show:11")
    assertEquals(ExistingForwardThenApply.show(11), "preserved-show:11")
    assertEquals(ExistingForwardThenApply.showCalls, 1)
  }

  test("a renamed delegated method remains structurally derived in composition") {
    assertEquals(RenamedComposition.render(17L), Text("rendered:17"))
  }
