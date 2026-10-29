# A small ledger consumer (development only)

This example uses the unreleased `0.2.0-SNAPSHOT` source build on JDK 25 and
exact Scala 3.3.8, 3.8.4 or 3.9.0. It is not a feature claim for public v0.1.0.
The complete, copyable ordinary Scala sources are
[`Algebra.scala`](../qualification/external-consumer/src/main/scala/ledger/Algebra.scala),
[`Amounts.scala`](../qualification/external-consumer/src/main/scala/ledger/Amounts.scala)
and [`LedgerWorkflow.scala`](../qualification/external-consumer/src/main/scala/ledger/LedgerWorkflow.scala).
They import only public AUXify marker annotations and the Scala standard library.

The calculation uses independent givens: a generated `Monoid.instance` supplies
addition; an `Addition[Int]` adapts that operation to binary syntax; `Show.show`
forwards the calculated total to a unary display instance. A bounded `Sum` adds
two `Credits` values and retains a precise dependent result. Default `@self`
on the zero-parameter `Amount` trait supplies a coherent `Self` path.

For example, after copying `Algebra.scala`, ordinary user code can write:

```scala
import ledger.*

val monoid = Monoid.instance[Int](0, (left, right) => left + right)
given Monoid[Int] = monoid
given Addition[Int] with
  def combine(left: Int, right: Int): Int = Monoid[Int].combine(left, right)
given Show[Int] with
  def show(value: Int): String = s"total=$value"

import Addition.syntax.*
val total = 20.combine(22)
assert(Show.show(total) == "total=42")
assert(Monoid[Int].eq(monoid))
```

`@instance` and `@syntax` belong to separate traits here: the historical
parameterless/binary factory and the binary-only syntax source profiles differ.
The `@apply`/`@syntax` pair generates one primary extension. Inherited `twice`
and aliases stay on the trait, so call `Addition[Int].twice(21)`.

The workflow also makes two distinctions visible. Historical factory `empty`
is by-name: constructing the instance evaluates it zero times; two reads
raise the counter to two; delegated `Zero.empty[Int]` raises it to three.
The curried factory accepts a strict `A => A => A` expression: the expression
is evaluated once during construction, ordinary arguments are strict, and
repeated method calls reuse the carrier. A unary delegated call is
`Show.show(total)`; the parameterless forwarder is `Zero.empty[Int]`.

An abstract-result factory preserves a concrete refinement:

```scala
val format = Format.instance[Int, String]
val refined: format.Copy =:= String = summon
given Format[Int] = format
val selected = Format[Int]
val path: selected.Copy =:= selected.Out = summon
val unrefined: scala.util.NotGiven[selected.Out =:= String] = summon
```

Simple contextual `@apply` returns the plain trait statically while preserving
the given's object identity. Both supported annotation orders are checked in
the full workflow. Full bounded `@apply` and `@aux` retain one result:

```scala
val sum: Sum.Aux[Credits, Credits, Credits] = Sum[Credits, Credits]
val same: sum.Again =:= sum.Out = summon
val result: sum.Out = sum(new Credits(20), new Credits(22))
assert(result.value == 42)
```

From the product root, run the canonical reproducible audit:

```bash
./scripts/verify-real-consumer-coherence.sh
```

The script source-builds the accepted peers into SHA-keyed isolated Ivy and
Coursier stores, runs prepare/verification serially for all three exact compiler
lines, compiles the external consumer in a disposable directory, and tests
contributor bootstrap. Each consumer run prints:

```text
AUXIFY_SCALA3_REAL_CONSUMER_RUNTIME_PASS total=42 shown=total=42 emptyEvaluations=3 strictArguments=2 carrierEvaluations=1
```

Only after the complete matrix and bootstrap does the script print
`AUXIFY_SCALA3_REAL_CONSUMER_COHERENCE_PASS`. The external policy requires exactly
one Macro-Paradise plugin requirement, one exact-line handler classpath and one
SHA-256 artifact identity. Runtime contains the marker without AUXify handlers,
Macro-Paradise compiler/API or Quasiquotes implementation jars.

The development graph is source-built Macro-Paradise compiler/API
`0.2.0-SNAPSHOT` at `aae704ca42ff01ee44e663fb024c726a357716c7` and Quasiquotes
`0.4.0-SNAPSHOT` at `e5ee36156fa0ed75e5aa04de42c9eacb6db656fb`, with public
`sbt-macroparadise` adapter `0.1.1`. These snapshot versions are not a stable
public release recipe. Keep the public v0.1.0 quick start in the README separate.
The normal [contributor setup](../README.md#contributing-from-source)
continues to use `./scripts/bootstrap-dev.sh` before ordinary sbt/IntelliJ import.

Unary `Show[A] @syntax`, generic/nondefault `@self`, two abstract result members,
binary delegated forwarding, arbitrary stacked annotations, and general Scala 2
AUXify parity remain outside these bounded profiles. Aliases must use the
source-ordered roots admitted by their family; only abstract-type-first factories
also authorize their leading abstract result as a root. This audit performs no
version bump, remote artifact publication, tag or release.
