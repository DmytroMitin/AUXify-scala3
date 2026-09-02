package com.github.dmytromitin.auxify.integration.aux

class AuxIntegrationSuite extends munit.FunSuite:
  test("canonical public Aux alias is usable in values signatures and summon positions") {
    val instance: Add.Aux[Zero, One, One] = summon[Add[Zero, One]]
    val accepted: Add.Aux[Zero, One, One] = acceptAdd(instance)
    val result: One = accepted(new Zero, new One)

    assert(result.isInstanceOf[One])
    assertEquals(accepted.description, "zero-plus-one")
  }

  test("renamed source names generate one coherent public alias") {
    val instance: Combine.Aux[LeftValue, RightValue, ResultValue] =
      summon[Combine[LeftValue, RightValue]]

    assert(instance.isInstanceOf[Combine[?, ?]])
  }

  test("unrelated existing companion members survive public aux expansion") {
    val instance: ExistingCompanion.Aux[Zero, One, One] =
      summon[ExistingCompanion[Zero, One]]

    assert(instance.isInstanceOf[ExistingCompanion[?, ?]])
    assertEquals(ExistingCompanion.before, 41)
    assertEquals(ExistingCompanion.after, 43)
    assert(ExistingCompanion.Nested ne null)
  }

  test("a direct existing Aux type is preserved without a duplicate") {
    val preserved: ExistingAuxType.Aux = "preserved"

    assertEquals(preserved, "preserved")
    assertEquals(ExistingAuxType.retained, 7)
  }

  test("a same-spelling term Aux coexists with the generated type alias") {
    val instance: TermNamespace.Aux[Zero, One, One] =
      summon[TermNamespace[Zero, One]]

    assert(instance.isInstanceOf[TermNamespace[?, ?]])
    assertEquals(TermNamespace.Aux, 9)
  }

  private def acceptAdd(
      value: Add.Aux[Zero, One, One]
  ): Add.Aux[Zero, One, One] = value
