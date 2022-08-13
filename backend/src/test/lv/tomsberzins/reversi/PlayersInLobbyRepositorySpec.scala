package lv.tomsberzins.reversi

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import lv.tomsberzins.reversi.Repository.PlayersInLobbyRepositoryInMem
import lv.tomsberzins.reversi.domain.Player
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class PlayersInLobbyRepositorySpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  "Full CRUD flow" in {
    PlayersInLobbyRepositoryInMem[IO].flatMap(repo => {
      Player[IO]("test").flatMap(player => {
        repo.addPlayer(player) *> repo.getAllPlayers.asserting(playersInLobby => playersInLobby.size mustBe 1).flatMap(_ => {
          repo.removePlayer(player) *> repo.getAllPlayers.asserting(players => players.size mustBe 0)
        })
      })
    })
  }
}
