package com.github.dmytromitin.auxify.integration.instance

class InstanceIntegrationSuite extends munit.FunSuite:
  test("constructs and runs the canonical Monoid factory") {
    val intAddition: Monoid[Int] =
      Monoid.instance(0, _ + _)

    assertEquals(intAddition.empty, 0)
    assertEquals(intAddition.combine(20, 22), 42)
    assertEquals(Monoid.preserved, 41)
  }

  test("keeps the parameterless carrier by-name") {
    var evaluations = 0
    val observed: Monoid[Int] = Monoid.instance(
      {
        evaluations += 1
        evaluations
      },
      _ + _
    )

    assertEquals(evaluations, 0)
    assertEquals(observed.empty, 1)
    assertEquals(observed.empty, 2)
    assertEquals(evaluations, 2)
  }

  test("constructs a coherently renamed instance factory") {
    val words: Choice[String] =
      Choice.instance("fallback", (left, right) => s"$left/$right")

    assertEquals(words.fallback, "fallback")
    assertEquals(words.select("left", "right"), "left/right")
  }

  test("constructs the canonical abstract-type factory with a visible refinement") {
    val value: HasOut[Int] { type Out = String } =
      HasOut.instance[Int, String]
    val witness: value.Out =:= String = summon[value.Out =:= String]

    assertEquals(witness("typed"), "typed")
  }

  test("constructs renamed abstract-type roles with collision-free freshness") {
    val value: Container[Int] { type Element = String } =
      Container.instance[Int, String]
    val witness: value.Element =:= String = summon[value.Element =:= String]

    assertEquals(witness("renamed"), "renamed")
    assertEquals(Container.preserved, 142)
  }

  test("constructs and runs the canonical curried factory") {
    val value: Curried[Int] =
      Curried.instance[Int](a => b => a + b)

    assertEquals(value.combine(20)(22), 42)
    assertEquals(Curried.preserved, 242)
  }

  test("constructs renamed curried roles with a collision-fresh carrier") {
    val value: CurriedCollision[String] =
      CurriedCollision.instance[String](left => right => s"$left/$right")

    assertEquals(value.combineFunction("left")("right"), "left/right")
  }

  test("preserves a direct existing abstract-type instance factory") {
    val value: ExistingOut[Int] { type Out = String } =
      ExistingOut.instance[Int, String]
    val witness: value.Out =:= String = summon[value.Out =:= String]

    assertEquals(witness("existing"), "existing")
    assertEquals(ExistingOut.instanceCalls, 1)
  }

  test("keeps collision-safe carriers distinct from source names") {
    val values: Collision[Int] =
      Collision.instance(7, _ max _)

    assertEquals(values.emptyValue, 7)
    assertEquals(values.merge(20, 22), 22)
  }

  test("inherits the bounded concrete method and dispatches through combine") {
    var combineCalls = 0
    val derived = DerivedMonoid.instance[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(derived.twice(21), 42)
    assertEquals(combineCalls, 1)
    assertEquals(DerivedMonoid.preserved, 84)
  }

  test("inherits the concrete binary method and dispatches through combine") {
    var combineCalls = 0
    val derived = BinaryDerivedMonoid.instance[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(derived.combineAgain(20, 22), 42)
    assertEquals(combineCalls, 1)
    assertEquals(BinaryDerivedMonoid.preserved, 91)
  }

  test("inherits the concrete ternary method and dispatches through combine") {
    var combineCalls = 0
    val derived = TernaryDerivedMonoid.instance[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(derived.fold3(10, 12, 20), 42)
    assertEquals(combineCalls, 2)
    assertEquals(TernaryDerivedMonoid.preserved, 93)
  }

  test("inherits multiple mixed-arity methods on one generated instance") {
    var emptyEvaluations = 0
    var combineCalls = 0
    val derived = RichDerivedMonoid.instance[Int](
      {
        emptyEvaluations += 1
        emptyEvaluations
      },
      (left, right) =>
        combineCalls += 1
        left + right
    )

    assertEquals(emptyEvaluations, 0)
    assertEquals(derived.zeroLike, 1)
    assertEquals(emptyEvaluations, 1)
    assertEquals(derived.twice(21), 42)
    assertEquals(combineCalls, 1)
    assertEquals(derived.fold3(10, 12, 20), 42)
    assertEquals(combineCalls, 3)
    assertEquals(RichDerivedMonoid.preserved, 97)
  }

  test("inherits heterogeneous aliases and mixed-arity methods") {
    var combineCalls = 0
    val derived = HeterogeneousDerivedMonoid.instance[Int](
      0,
      (left, right) =>
        combineCalls += 1
        left + right
    )

    val item: derived.Item = 42
    val value: derived.Value = 42
    val itemEquality: derived.Item =:= Int = summon[derived.Item =:= Int]
    val valueEquality: derived.Value =:= Int = summon[derived.Value =:= Int]
    assertEquals(itemEquality(item), 42)
    assertEquals(valueEquality(value), 42)
    assertEquals(derived.twice(21), 42)
    assertEquals(derived.fold3(10, 12, 20), 42)
    assertEquals(combineCalls, 3)
    assertEquals(HeterogeneousDerivedMonoid.preserved, 98)
  }

  test("inherits a coherently renamed five-parameter concrete method") {
    var selectCalls = 0
    val choice = LargerDerivedChoice.instance[String](
      "fallback",
      (left, right) =>
        selectCalls += 1
        s"$left/$right"
    )

    assertEquals(
      choice.fold5("a", "b", "c", "d", "e"),
      "a/b/c/d/e"
    )
    assertEquals(selectCalls, 4)
    assertEquals(LargerDerivedChoice.preserved, 95)
  }

  test("inherits a coherently renamed concrete binary method") {
    val choice = BinaryDerivedChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    assertEquals(choice.selectAgain("left", "right"), "left/right")
  }

  test("preserves a direct existing instance for the concrete binary family") {
    val derived = ExistingBinaryDerived.instance[Int](0, _ + _)

    assertEquals(derived.combineAgain(20, 22), 42)
    assertEquals(ExistingBinaryDerived.instanceCalls, 1)
  }

  test("inherits the concrete parameterless method and dispatches through the by-name empty override") {
    var evaluations = 0
    val zero = ZeroMonoid.instance[Int](
      {
        evaluations += 1
        evaluations
      },
      _ + _
    )

    assertEquals(evaluations, 0)
    assertEquals(zero.zero, 1)
    assertEquals(zero.empty, 2)
    assertEquals(evaluations, 2)
    assertEquals(zero.combine(20, 22), 42)
    assertEquals(ZeroMonoid.preserved, 105)
  }

  test("inherits a coherently renamed concrete parameterless method") {
    val choice = ZeroChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    assertEquals(choice.defaultValue, "fallback")
    assertEquals(choice.select("left", "right"), "left/right")
  }

  test("preserves a direct existing instance for the concrete parameterless family") {
    val zero = ExistingZero.instance[Int](6, _ + _)

    assertEquals(zero.zero, 6)
    assertEquals(zero.combine(20, 22), 42)
    assertEquals(ExistingZero.instanceCalls, 1)
  }

  test("inherits a coherently renamed concrete unary method") {
    val derived = DerivedChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    assertEquals(derived.duplicate("same"), "same/same")
  }

  test("inherits a concrete type alias and preserves by-name factory semantics") {
    var evaluations = 0
    val wrapped = WrappedMonoid.instance[Int](
      {
        evaluations += 1
        0
      },
      _ + _
    )

    val item: wrapped.Item = 42
    val equality: wrapped.Item =:= Int = summon[wrapped.Item =:= Int]
    assertEquals(equality(item), 42)
    assertEquals(evaluations, 0)
    assertEquals(wrapped.empty, 0)
    assertEquals(evaluations, 1)
    assertEquals(wrapped.combine(20, 22), 42)
    assertEquals(WrappedMonoid.preserved, 126)
  }

  test("inherits a coherently renamed concrete type alias family") {
    val wrapped = WrappedChoice.instance[String](
      "fallback",
      (left, right) => s"$left/$right"
    )

    val value: wrapped.Value = "typed"
    assertEquals(value, "typed")
    assertEquals(wrapped.fallback, "fallback")
    assertEquals(wrapped.select("left", "right"), "left/right")
  }

  test("preserves a direct existing instance for the alias family") {
    val wrapped = ExistingWrapped.instance[Int](6, _ + _)
    val item: wrapped.Item = 6

    assertEquals(item, 6)
    assertEquals(wrapped.combine(20, 22), 42)
    assertEquals(ExistingWrapped.instanceCalls, 1)
  }
