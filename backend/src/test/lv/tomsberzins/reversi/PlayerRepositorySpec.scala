package lv.tomsberzins.reversi

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import lv.tomsberzins.reversi.Repository.PlayerRepositoryInMemory
import lv.tomsberzins.reversi.domain.Player
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class PlayerRepositorySpec extends AsyncFreeSpec with AsyncIOSpec with Matchers{
  "Player can be created and then found" in {
    PlayerRepositoryInMemory[IO].flatMap(repo => {
      Player[IO]("test").flatMap(player => {
        repo.createPlayer(player) *> repo.getPlayerById(player.id).asserting(optPlayer => {
          optPlayer.get.name mustBe "test"
        })
      })
    })
  }

  "Try to find nonexistant player" in {
    PlayerRepositoryInMemory[IO].flatMap(repo => {
      repo.getPlayerById("").asserting(optPlayer => {
        optPlayer.isEmpty mustBe true
      })
    })
  }
}
