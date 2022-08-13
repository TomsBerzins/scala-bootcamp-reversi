package lv.tomsberzins.reversi

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import lv.tomsberzins.reversi.Repository.GameManagerRepository
import lv.tomsberzins.reversi.domain.{Game, GameEnded, GameInProgress, Player}
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock

class GameManagerRepositorySpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with EitherValues {
  "Try to get game manager for non-existent game" in {
    GameManagerRepository[IO].flatMap(gm => {
      gm.getGameManager("").asserting(eitherGm => {
        eitherGm.left.value mustBe "Game with such id not found"
      })
    })
  }

  "Get game manager for a game that's already ended" in {
    val gameMock = mock[Game]
    when(gameMock.gameStatus).thenReturn(GameEnded)
    when(gameMock.id).thenReturn("1")

    GameManagerRepository[IO].flatMap(gm => {
     gm.tryCreateGameManagerForGame(gameMock) *> gm.getGameManager(gameMock.id).asserting(eitherGm => {
       eitherGm.left.value mustBe "Game has ended"
     })
    })
  }

  "Get game manager for a game that is in progress" in {
    val gameMock = mock[Game]
    when(gameMock.gameStatus).thenReturn(GameInProgress)
    when(gameMock.id).thenReturn("1")

    GameManagerRepository[IO].flatMap(gm => {
      gm.tryCreateGameManagerForGame(gameMock) *> gm.getGameManager(gameMock.id).flatMap(eitherGm => {
        eitherGm.value.getGame.asserting(_ mustBe gameMock)
      })
    })
  }

  "Try to create a game manager for game whose owner already has a game" in {
    val player = mock[Player]
    when(player.id).thenReturn("1")

    val game1 = mock[Game]
    when(game1.id).thenReturn("game1")
    when(game1.createdBy).thenReturn(player)

    val game2 = mock[Game]
    when(game2.id).thenReturn("game2")
    when(game2.createdBy).thenReturn(player)

    GameManagerRepository[IO].flatMap(gm => {
      gm.tryCreateGameManagerForGame(game1) *> gm.tryCreateGameManagerForGame(game2).asserting(eitherGm => {
        eitherGm.left.value mustBe "You already have an active game"
      })
    })
  }

  "Game manager getAllActiveGames should not return ended games" in {
    val gameEndedMock = mock[Game]
    val gameEndedOwner = mock[Player]
    when(gameEndedMock.gameStatus).thenReturn(GameEnded)
    when(gameEndedMock.createdBy).thenReturn(gameEndedOwner)
    when(gameEndedMock.id).thenReturn("1")

    val gameActiveMock = mock[Game]
    val gameActiveOwner = mock[Player]
    when(gameActiveMock.gameStatus).thenReturn(GameInProgress)
    when(gameActiveMock.createdBy).thenReturn(gameActiveOwner)
    when(gameActiveMock.id).thenReturn("2")

    GameManagerRepository[IO].flatMap(gm => {
      gm.tryCreateGameManagerForGame(gameActiveMock) *> gm.tryCreateGameManagerForGame(gameEndedMock) *> gm.getAllActiveGames.asserting(games => {
        games.length mustBe 1
        games.head mustBe gameActiveMock
      })
    })
  }
}
