package com.github.dmytromitin.auxify.integration.modifieraudit

class CurrentMethodModifierAdmissionIntegrationSuite extends munit.FunSuite:
  test("characterizes plugin-backed infix instance admission without declaring support") {
    val current = CurrentlyAdmittedInfixInstance.instance(0, _ + _)

    assertEquals(current.empty, 0)
    assertEquals(current.combine(20, 22), 42)
    assertEquals(current twice 21, 42)
  }

  test("characterizes plugin-backed infix delegated admission without declaring support") {
    val evidence = summon[CurrentlyAdmittedInfixDelegated[Int]]

    assertEquals(evidence show 42, "42")
    assertEquals(CurrentlyAdmittedInfixDelegated.show(42), "42")
  }

  test("characterizes accidental success in both supported apply-instance source orders") {
    val first = CurrentlyAdmittedInfixApplyThenInstance.instance(0, _ + _)
    given CurrentlyAdmittedInfixApplyThenInstance[Int] = first
    assert(CurrentlyAdmittedInfixApplyThenInstance[Int].eq(first))

    val second = CurrentlyAdmittedInfixInstanceThenApply.instance(0, _ + _)
    given CurrentlyAdmittedInfixInstanceThenApply[Int] = second
    assert(CurrentlyAdmittedInfixInstanceThenApply[Int].eq(second))
  }
