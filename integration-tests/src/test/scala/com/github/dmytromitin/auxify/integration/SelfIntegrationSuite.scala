package com.github.dmytromitin.auxify.integration

class SelfIntegrationSuite extends munit.FunSuite:
  test("anonymous self exposes the default lower, upper, and F-bound semantics") {
    val nat = new Nat {}
    val generatedSelf: nat.Self = nat
    val upperBound: Nat = generatedSelf
    val coherent: generatedSelf.Self = generatedSelf

    assert(upperBound eq nat)
    assert(coherent eq nat)
  }

  test("an existing named self alias is retained with generated Self semantics") {
    val nat = new NamedNat {}
    val generatedSelf: nat.Self = nat
    val coherent: generatedSelf.Self = generatedSelf

    assert(nat.retainedNamedAlias eq nat)
    assert(coherent eq nat)
  }

  test("occupied self and self$1 names admit the generated self$2 alias") {
    val nat = new CollisionNat {}
    val generatedSelf: nat.Self = nat
    val coherent: generatedSelf.Self = generatedSelf

    assertEquals(nat.self, 1)
    assertEquals(nat.self$1, 2)
    assert(coherent eq nat)
  }
