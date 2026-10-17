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

  test("apply then aux preserves concrete aliases as inherited type members") {
    val instance: RichApplyThenAux.Aux[AuxZero, AuxOne, AuxOne] =
      RichApplyThenAux[AuxZero, AuxOne]
    val left: instance.Left =:= AuxZero = summon
    val right: instance.Right =:= AuxOne = summon
    val out: instance.Out =:= AuxOne = summon
    val copyOut: instance.Copy =:= instance.Out = summon
    val copyResult: instance.Copy =:= AuxOne = summon
    val result: AuxOne = instance(new AuxZero, new AuxOne)

    assert(left ne null)
    assert(right ne null)
    assert(out ne null)
    assert(copyOut ne null)
    assert(copyResult ne null)
    assert(result.isInstanceOf[AuxOne])
    assertEquals(RichApplyThenAux.preserved, 121)
  }

  test("aux then apply preserves concrete aliases as inherited type members") {
    val instance: RichAuxThenApply.Aux[AuxZero, AuxOne, AuxOne] =
      RichAuxThenApply[AuxZero, AuxOne]
    val left: instance.Left =:= AuxZero = summon
    val right: instance.Right =:= AuxOne = summon
    val out: instance.Out =:= AuxOne = summon
    val copyOut: instance.Copy =:= instance.Out = summon
    val copyResult: instance.Copy =:= AuxOne = summon
    val result: AuxOne = instance(new AuxZero, new AuxOne)

    assert(left ne null)
    assert(right ne null)
    assert(out ne null)
    assert(copyOut ne null)
    assert(copyResult ne null)
    assert(result.isInstanceOf[AuxOne])
    assertEquals(RichAuxThenApply.preserved, 242)
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
