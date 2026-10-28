package com.github.dmytromitin.auxify.integration.instance

class AbstractRootIntegrationSuite extends munit.FunSuite:
  test("RootedStandalone inherits mixed root chains with a precise generated factory") {
    val constructed: RootedStandalone[Int] { type Out = String } = RootedStandalone.instance[Int, String]
    val outProof: constructed.Out =:= String = summon
    val copyProof: constructed.Copy =:= String = summon
    val againProof: constructed.Again =:= String = summon
    val itemProof: constructed.Item =:= Int = summon
    val fresh0Proof: constructed.Out0 =:= String = summon
    val fresh1Proof: constructed.Out1 =:= Int = summon
    assertEquals(constructed.identityOut(copyProof.flip("typed")), "typed")
    assertEquals(constructed.directOut(outProof.flip("value")), "value")
    assertEquals(constructed.mixed(itemProof.flip(42), againProof.flip("mixed")), "mixed")
    assertEquals(constructed.identityA(42), 42)
    assertEquals(constructed.Out2(99), 99)
    assertEquals(fresh0Proof("fresh"), "fresh")
    assertEquals(fresh1Proof(42), 42)
    intercept[NoSuchElementException](constructed.zero)
  }

  test("RootedForward inherits mixed root chains with a precise generated factory") {
    val constructed: RootedForward[Int] { type Out = String } = RootedForward.instance[Int, String]
    val outProof: constructed.Out =:= String = summon
    val copyProof: constructed.Copy =:= String = summon
    val againProof: constructed.Again =:= String = summon
    val itemProof: constructed.Item =:= Int = summon
    val fresh0Proof: constructed.Out0 =:= String = summon
    val fresh1Proof: constructed.Out1 =:= Int = summon
    assertEquals(constructed.identityOut(copyProof.flip("typed")), "typed")
    assertEquals(constructed.directOut(outProof.flip("value")), "value")
    assertEquals(constructed.mixed(itemProof.flip(42), againProof.flip("mixed")), "mixed")
    assertEquals(constructed.identityA(42), 42)
    assertEquals(constructed.Out2(99), 99)
    assertEquals(fresh0Proof("fresh"), "fresh")
    assertEquals(fresh1Proof(42), 42)
    intercept[NoSuchElementException](constructed.zero)
    given RootedForward[Int] = constructed
    val selected: RootedForward[Int] = RootedForward[Int]
    val pathProof: selected.Copy =:= selected.Out = summon
    val pathAgain: selected.Again =:= selected.Out = summon
    val itemSelected: selected.Item =:= Int = summon
    assert(selected.eq(constructed))
    assertEquals(itemSelected(selected.identityA(42)), 42)
    // Simple apply deliberately retains its unrefined contextual result.
    assert(pathProof != null && pathAgain != null)
  }

  test("RootedReverse inherits mixed root chains with a precise generated factory") {
    val constructed: RootedReverse[Int] { type Result = String } = RootedReverse.instance[Int, String]
    val outProof: constructed.Result =:= String = summon
    val copyProof: constructed.Copy =:= String = summon
    val againProof: constructed.Again =:= String = summon
    val itemProof: constructed.Item =:= Int = summon
    val fresh0Proof: constructed.Result0 =:= String = summon
    val fresh1Proof: constructed.Result1 =:= Int = summon
    assertEquals(constructed.identityOut(copyProof.flip("typed")), "typed")
    assertEquals(constructed.directOut(outProof.flip("value")), "value")
    assertEquals(constructed.mixed(itemProof.flip(42), againProof.flip("mixed")), "mixed")
    assertEquals(constructed.identityA(42), 42)
    assertEquals(constructed.Result2(99), 99)
    assertEquals(fresh0Proof("fresh"), "fresh")
    assertEquals(fresh1Proof(42), 42)
    intercept[NoSuchElementException](constructed.zero)
    given RootedReverse[Int] = constructed
    val selected: RootedReverse[Int] = RootedReverse[Int]
    val pathProof: selected.Copy =:= selected.Result = summon
    val pathAgain: selected.Again =:= selected.Result = summon
    val itemSelected: selected.Item =:= Int = summon
    assert(selected.eq(constructed))
    assertEquals(itemSelected(selected.identityA(42)), 42)
    // Simple apply deliberately retains its unrefined contextual result.
    assert(pathProof != null && pathAgain != null)
  }

  test("renamed Output and both former controlled negatives are real generated positives") {
    val renamed = OutputOnly.instance[Int, String]
    val proof: renamed.Again =:= String = summon
    assertEquals(renamed.identity(proof.flip("output")), "output")
    val alias = TypeMemberOutRootAlias.instance[Int, String]
    assertEquals(summon[alias.Item =:= String]("alias"), "alias")
    val method = TypeMemberOutMethod.instance[Int, String]
    assertEquals(method.identity("method"), "method")
  }
