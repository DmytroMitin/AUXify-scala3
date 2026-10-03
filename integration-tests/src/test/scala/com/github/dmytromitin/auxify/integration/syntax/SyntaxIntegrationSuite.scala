package com.github.dmytromitin.auxify.integration.syntax

class SyntaxIntegrationSuite extends munit.FunSuite:
  test("canonical native extension syntax returns 42") {
    assertEquals(SyntaxRuntime.canonical, 42)
  }

  test("renamed roles and fresh evidence name return 42") {
    assertEquals(SyntaxRuntime.renamed, 42)
  }

  test("existing unrelated companion members survive in order") {
    assertEquals(SyntaxRuntime.existingCompanion, 42)
    assertEquals(ExistingCompanion.before, 20)
    assertEquals(ExistingCompanion.after, 22)
  }

  test("direct syntax term conflict preserves the existing module") {
    assertEquals(ExistingSyntax.syntax.retained, 42)
  }

  test("type syntax remains separate from generated term syntax") {
    import TypeSyntax.syntax.*
    given TypeSyntax[Int] with
      def join(left: Int, right: Int): Int = left + right

    val typeWitness: TypeSyntax.syntax = "retained"
    assertEquals(typeWitness, "retained")
    assertEquals(20.join(22), 42)
  }

  test("nested syntax does not conflict with generated direct syntax") {
    import NestedSyntax.syntax.*
    given NestedSyntax[Int] with
      def join(left: Int, right: Int): Int = left + right

    assertEquals(NestedSyntax.Nested.syntax.retained, 42)
    assertEquals(20.join(22), 42)
  }
