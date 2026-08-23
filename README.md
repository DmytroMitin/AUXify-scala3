# AUXify-scala3

AUXify-scala3 currently provides an experimental first Scala 3 `@apply`
milestone. The current proof is specific to Scala 3.8.4 on JDK 25.

For a supported generic trait such as:

```scala
import com.github.dmytromitin.auxify.macros.apply

@apply
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[String] with
    def show(a: String): String = a

val stringShow: Show[String] = Show[String]
```

the annotation conceptually adds this contextual materializer to the
companion:

```scala
def apply[A](using inst: Show[A]): Show[A] = inst
```

The proven milestone creates a missing companion, or preserves an existing
companion and adds the materializer when it has no direct member named
`apply`. An existing direct `apply` is preserved and is not duplicated.

The implementation depends on the experimental Scala 3 Macro-Paradise
compiler plugin and quasiquotes libraries. These dependencies are currently
unreleased for this integration: preparing them through local publication is
a development-only step. This README makes no claim that the required AUXify,
Macro-Paradise, or quasiquotes artifacts are available from a remote artifact
repository.

The verified target is deliberately narrow: one top-level, non-sealed,
ordinary trait with exactly one invariant, unbounded type parameter and no
constructor or value parameters. Path-dependent type-member materialization
such as `Add.Out` is not supported or claimed by this milestone; it is reserved
for a later milestone.
