package lv.tomsberzins.reversi

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import lv.tomsberzins.reversi.domain.Player
import org.scalatest.matchers.should.Matchers
import org.scalatest.freespec.AsyncFreeSpec

class PlayerSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  "Player creation requires name only and generates id with 36 chars(uuid)" in {
     Player[IO]("someName").asserting(p => {
       p.name shouldBe "someName"
       p.id.length shouldBe 36
     })
  }
}
