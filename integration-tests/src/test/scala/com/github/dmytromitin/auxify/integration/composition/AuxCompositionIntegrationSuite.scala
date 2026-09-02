package com.github.dmytromitin.auxify.integration.composition

class AuxCompositionIntegrationSuite extends munit.FunSuite:
  test("apply then aux exposes both generated companion capabilities") {
    val instance: ApplyThenAux.Aux[AuxZero, AuxOne, AuxOne] =
      ApplyThenAux[AuxZero, AuxOne]
    val result: AuxOne = instance(new AuxZero, new AuxOne)

    assert(result.isInstanceOf[AuxOne])
    assertEquals(ApplyThenAux.preserved, 41)
  }

  test("aux then apply exposes both generated companion capabilities") {
    val instance: AuxThenApply.Aux[AuxZero, AuxOne, AuxOne] =
      AuxThenApply[AuxZero, AuxOne]
    val result: AuxOne = instance(new AuxZero, new AuxOne)

    assert(result.isInstanceOf[AuxOne])
    assertEquals(AuxThenApply.preserved, 84)
  }

  test("existing apply is preserved while the Aux alias is generated") {
    val instance: ExistingApplyThenAux.Aux[AuxZero, AuxOne, AuxOne] =
      summon[ExistingApplyThenAux[AuxZero, AuxOne]]

    assert(instance.isInstanceOf[ExistingApplyThenAux[?, ?]])
    assert(ExistingApplyThenAux[AuxZero, AuxOne] eq instance)
    assertEquals(ExistingApplyThenAux.applyCalls, 1)
  }

  test("existing Aux type is preserved while apply is generated") {
    val preserved: ExistingAuxThenApply.Aux = "preserved"
    val instance = ExistingAuxThenApply[AuxZero, AuxOne]

    assertEquals(preserved, "preserved")
    assertEquals(ExistingAuxThenApply.retained, 7)
    assert(instance.isInstanceOf[ExistingAuxThenApply[?, ?]])
  }
