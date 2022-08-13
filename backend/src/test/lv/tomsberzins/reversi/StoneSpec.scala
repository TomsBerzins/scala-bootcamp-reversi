package lv.tomsberzins.reversi

import lv.tomsberzins.reversi.domain.{BlackStone, WhiteStone}
import org.scalatest.flatspec.AnyFlatSpec

class StoneSpec  extends AnyFlatSpec {

  "Stone" should "be flippable to its opposite color" in {
    assertResult(BlackStone()) {
      WhiteStone().flip()
    }
    assertResult(WhiteStone()) {
      BlackStone().flip()
    }
  }
  it should "return same color if flipped again" in {
    assertResult(WhiteStone()) {
      WhiteStone().flip().flip()
    }
  }

}
