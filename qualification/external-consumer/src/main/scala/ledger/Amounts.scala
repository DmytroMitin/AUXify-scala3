package ledger

import com.github.dmytromitin.auxify.macros.{apply, aux, self}

@self
trait Amount:
  def value: Int

final class Credits(val value: Int) extends Amount

@aux
@apply
trait Sum[N <: Amount, M <: Amount]:
  type Out <: Amount
  type Copy = Out
  type Again = Copy
  type Left = N
  def apply(left: N, right: M): Out
  type Right = M

object Sum:
  given Sum[Credits, Credits] with
    type Out = Credits
    def apply(left: Credits, right: Credits): Credits =
      new Credits(left.value + right.value)
