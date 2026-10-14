package com.github.dmytromitin.auxify.integration.composition

class ApplySyntaxCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then syntax already composes without an inherited tail") {
    import ApplyThenSyntaxNoTail.syntax.*

    assertEquals(20.combine(22), 42)
    assertEquals(ApplyThenSyntaxNoTail[Int].combine(20, 22), 42)
  }

  test("syntax then apply already composes without an inherited tail") {
    import SyntaxThenApplyNoTail.syntax.*

    assertEquals(20.merge(22), 42)
    assertEquals(SyntaxThenApplyNoTail[Int].merge(20, 22), 42)
  }
