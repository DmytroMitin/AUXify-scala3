package ledger

import scala.util.NotGiven

/** Public source consumer: no handler, compiler, or Quasiquotes imports. */
object LedgerWorkflow:
  def verify(): Unit =
    var emptyEvaluations = 0
    val monoid = Monoid.instance[Int](
      { emptyEvaluations += 1; 0 },
      (left, right) => left + right
    )
    given Monoid[Int] = monoid
    assert(emptyEvaluations == 0)
    assert(Monoid[Int].eq(monoid))
    assert(monoid.empty == 0 && emptyEvaluations == 1)
    assert(monoid.empty == 0 && emptyEvaluations == 2)

    given Addition[Int] with
      def combine(left: Int, right: Int): Int = Monoid[Int].combine(left, right)
    import Addition.syntax.*
    val total = 20.combine(22)
    assert(total == 42)
    val addition = Addition[Int]
    val alias: addition.Copy =:= Int = summon
    assert(alias(addition.twice(21)) == total)

    var argumentEvaluations = 0
    var carrierCalls = 0
    var carrierEvaluations = 0
    val curried = CurriedAddition.instance[Int]({
      carrierEvaluations += 1
      left => right =>
        carrierCalls += 1
        monoid.combine(left, right)
    })
    assert(carrierEvaluations == 1 && carrierCalls == 0)
    given CurriedAddition[Int] = curried
    assert(CurriedAddition[Int].eq(curried))
    val pending = curried.combine({ argumentEvaluations += 1; 20 })
    assert(argumentEvaluations == 1 && carrierCalls == 0)
    assert(pending({ argumentEvaluations += 1; 22 }) == total)
    assert(argumentEvaluations == 2 && carrierCalls == 1)
    assert(curried.combine(10)(32) == total)
    assert(carrierEvaluations == 1 && carrierCalls == 2)

    given Zero[Int] with
      def empty: Int = monoid.empty
    val stableZero: Int = Zero.empty[Int] // parameterless select, no empty ()
    assert(stableZero == 0 && emptyEvaluations == 3)
    given Show[Int] with
      def show(value: Int): String = s"total=$value"
    val display = Show[Int]
    val shown = Show.show(display.retain(total))
    assert(shown == "total=42")

    val format: Format[Int] { type Out = String } = Format.instance[Int, String]
    val refinedOut: format.Out =:= String = summon
    val refinedAlias: format.Copy =:= String = summon
    assert(refinedOut(format.retain(refinedAlias.flip(shown))) == shown)
    given Format[Int] = format
    val selected = Format[Int]
    val unrefined: NotGiven[selected.Out =:= String] = summon
    val pathAlias: selected.Copy =:= selected.Out = summon
    val inputAlias: selected.Item =:= Int = summon
    assert(selected.eq(format) && inputAlias(selected.input(total)) == total)
    // Simple apply intentionally does not return Out = String statically.
    val receipt = Receipt.instance[Int, String]
    val receiptAlias: receipt.Copy =:= String = summon
    given Receipt[Int] = receipt
    val selectedReceipt = Receipt[Int]
    val receiptUnrefined: NotGiven[selectedReceipt.Out =:= String] = summon
    val receiptPath: selectedReceipt.Copy =:= selectedReceipt.Out = summon
    assert(selectedReceipt.eq(receipt))
    assert(receiptAlias(receipt.retain(shown)) == shown)

    val sum: Sum.Aux[Credits, Credits, Credits] = Sum[Credits, Credits]
    val resultType: sum.Out =:= Credits = summon
    val resultAlias: sum.Again =:= sum.Out = summon
    val leftAlias: sum.Left =:= Credits = summon
    val rightAlias: sum.Right =:= Credits = summon
    val result: sum.Out = sum(leftAlias.flip(new Credits(20)), rightAlias.flip(new Credits(22)))
    val copied: sum.Again = resultAlias.flip(result)
    assert(resultType(resultAlias(copied)).value == total)
    assert(Show.show(result.value) == shown)
    val selfValue: result.Self = result
    val selfAgain: selfValue.Self = selfValue
    assert(selfAgain.eq(result))

    println(s"AUXIFY_SCALA3_REAL_CONSUMER_RUNTIME_PASS total=$total shown=$shown emptyEvaluations=$emptyEvaluations strictArguments=$argumentEvaluations carrierEvaluations=$carrierEvaluations")
