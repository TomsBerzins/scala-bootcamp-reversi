package lv.tomsberzins.reversi

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId}
import fs2.concurrent.InspectableQueue
import lv.tomsberzins.reversi.Messages.Websocket.GameMessage._
import lv.tomsberzins.reversi.domain._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.EitherValues
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar._


class GameManagerSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with EitherValues{
  val gameMock = mock[Game]

  "Newly created game manager should have empty map of player queues" in {
    GameManager[IO](gameMock).flatMap(gm => {
      gm.data.get.asserting(gm => {
        gm._1.size shouldBe 0
        gm._2 shouldBe gameMock
      })
    })
  }

  "Register players for game using game manager" - {
    "Game has two players, try to register 3rd one" in {
      val player = mock[Player]
      when(gameMock.isFull).thenReturn(true)
      when(gameMock.isPlayerRegistered(player)).thenReturn(false)

      GameManager[IO](gameMock).flatMap(gm => {
        gm.registerPlayerForGame(player).asserting(_.left.value shouldBe "Game is full")
      })
    }
    "Game has two players, try to register already existing player" in {
      val player = mock[Player]
      when(player.id).thenReturn("other-player-id")

      when(gameMock.isFull).thenReturn(true)
      when(gameMock.isPlayerRegistered(player)).thenReturn(true)

      GameManager[IO](gameMock).flatMap(gm => {
          for {
            _ <- gm.registerPlayerForGame(player)
            data <- gm.data.get
            _ <- gm.bothPlayersOnline().asserting(_ shouldBe false)
          } yield {
            data._1.keys should contain (player.id )
          }
      })
    }
    "Register owner of game and other player" in {
      val gameOwner = mock[Player]
      val otherPlayer = mock[Player]
      when(otherPlayer.id).thenReturn("other-player-id")

      for {
        game <- Game[IO]("someName", gameOwner)
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        data <- gm.data.get
        _ <- gm.bothPlayersOnline().asserting(_ shouldBe true)
      } yield {
        data._2.playerToStoneMap should contain (otherPlayer -> WhiteStone())
        data._1.keys should contain (otherPlayer.id)

      }
    }
  }

  "Game manager set game in progress" in {
    GameManager[IO](gameMock).flatMap(gm => {
      gm.setGameInProgress().asserting(game => {
        game.gameStatus shouldBe GameInProgress
      })
    })
    val gameOwner = mock[Player]
    for {
      game <- Game[IO]("someName", gameOwner)
      gm <- GameManager[IO](game)
      updatedGame <- gm.setGameInProgress()
    } yield {
      updatedGame.gameStatus shouldBe GameInProgress
    }
  }

  "Remove player queue" - {
    "Try to remove nonexistent player queue" in {
      GameManager[IO](gameMock).flatMap(gm => {
         gm.removeQueueForPlayer("some-id").asserting(_._1.size shouldBe 0)
      })
    }
    "Try to remove existing player queue" in {
      val player = mock[Player]

      for {
        game <- Game[IO]("someName", player)
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(player)
        _ <- gm.data.get.asserting(_._1.size shouldBe 1)
        _ <- gm.removeQueueForPlayer(player.id)
        data <- gm.data.get

      } yield {
          data._1.size shouldBe 0
      }
    }
  }

  "Game manager message publishing" - {
    "Publish message to both players" in {
      val gameOwner = mock[Player]
      val otherPlayer = mock[Player]
      val gameOutputMessage = mock[GameOutputCommand]

      when(otherPlayer.id).thenReturn("other-player-id")

      for {
        game <- Game[IO]("someName", gameOwner)
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.publishToBothPlayers(gameOutputMessage)
        data <- gm.data.get
        ownerQueueSize <- data._1(gameOwner.id).getSize
        otherPlayerQueueSize <- data._1(otherPlayer.id).getSize
      } yield {
        assert(ownerQueueSize == 1)
        assert(otherPlayerQueueSize == 1)
      }
    }
    "Publish message to specific player" in {
      val gameOwner = mock[Player]
      val otherPlayer = mock[Player]
      val gameOutputMessage = mock[GameOutputCommand]

      when(otherPlayer.id).thenReturn("other-player-id")

      for {
        game <- Game[IO]("someName", gameOwner)
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.publishToSpecificPlayer(otherPlayer.id, gameOutputMessage)
        data <- gm.data.get
        ownerQueueSize <- data._1(gameOwner.id).getSize
        otherPlayerQueueSize <- data._1(otherPlayer.id).getSize
      } yield {
        assert(ownerQueueSize == 0)
        assert(otherPlayerQueueSize == 1)
      }
    }
  }

  "Player1 connects and publishes game state, player2 connects and publishes game state" - {
    val gameOwner = mock[Player]
    when(gameOwner.id) thenReturn("owner-id")

    val otherPlayer = mock[Player]
    when(otherPlayer.id) thenReturn("otherplayer-id")

    val gmWithTwoPlayersRegistered = for {
      game <- Game[IO]("someName", gameOwner)
      gm <- GameManager[IO](game)
      _ <- gm.registerPlayerForGame(gameOwner)
    } yield gm

    "Player1 connects and publishes game state" in {
      for {
          emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
          gm <- gmWithTwoPlayersRegistered
          _ <- gm.publishGameStateMessage(gameOwner.id)
          data <- gm.data.get
          gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
          _ <- gameOwnerQ.dequeue1.asserting(msg => {
            msg shouldBe GameServerMessage("Waiting for other player to join", "game-waiting-for-opponent")
          })
          _ <- gameOwnerQ.tryDequeue1.asserting(msg => {
            msg shouldBe None
          })
          // second player joins
          _ <- gm.registerPlayerForGame(otherPlayer)
          _ <- gm.publishGameStateMessage(otherPlayer.id)
          data <- gm.data.get
          // game owner (player1) now should see that game has started and whoever needs to move
          gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
          _ <- gameOwnerQ.dequeue1.asserting(msg => {
            msg shouldBe GameServerMessage("Game started", "game-started")
          })
          _ <- gameOwnerQ.dequeue1.asserting(msg => {
            msg shouldBe a [PlayerNextToMove]
          })
          _ <- gameOwnerQ.tryDequeue1.asserting(msg => {
            msg shouldBe None
          })
          otherPlayerQueue <- data._1.getOrElse(otherPlayer.id, emptyQ).pure[IO]
          _ <- otherPlayerQueue.dequeue1.asserting(msg => {
            msg shouldBe GameServerMessage("Game started", "game-started")
          })
          _ <- otherPlayerQueue.dequeue1.asserting(msg => {
            msg shouldBe a [PlayerNextToMove]
          })
          _ <- otherPlayerQueue.tryDequeue1.asserting(msg => {
            msg shouldBe None
          })
        } yield succeed

      }
  }

  "Game already in progress, publishing its state returns next player to move" in {
    val gameOwner = mock[Player]
    when(gameOwner.id) thenReturn("owner-id")

    val otherPlayer = mock[Player]
    when(otherPlayer.id) thenReturn("otherplayer-id")

    val game = mock[Game]
    when(game.inProgress) thenReturn(true)
    when(game.getPlayerNextToMove) thenReturn(gameOwner.some)
    when(game.isPlayerRegistered(gameOwner)) thenReturn(true)

    for {
      emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
      gm <- GameManager[IO](game)
      _ <- gm.registerPlayerForGame(gameOwner)
      _ <- gm.publishGameStateMessage(gameOwner.id)
      data <- gm.data.get
      gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
      _ <- gameOwnerQ.dequeue1.asserting(msg => {
        msg shouldBe PlayerNextToMove(gameOwner, game)
      })
      _ <- gameOwnerQ.tryDequeue1.asserting(msg => {
        msg shouldBe None
      })
    } yield succeed
  }

  "Game manager handle player input" - {
    "Handle invalid player input" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      for {
        game <- Game[IO]("someName", gameOwner)
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.handlePlayerInput(Invalid("some-command"), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(msg => {
          msg shouldBe Invalid("some-command")
        })
        _ <- gameOwnerQ.tryDequeue1.asserting(msg => {
          msg shouldBe None
        })
      } yield succeed
    }
    "Handle player leave" in {

      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      val otherPlayer = mock[Player]
      when(otherPlayer.id) thenReturn("otherplayer-id")

      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        game <- Game[IO]("someName", gameOwner)
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.handlePlayerInput(GameInputPlayerLeft(otherPlayer), otherPlayer)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(msg => {
          msg shouldBe GameInputPlayerLeft(otherPlayer)
        })
      } yield {
        data._1.get(otherPlayer.id) shouldBe None
      }
    }
    "Handle player move when game not started" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")


      val game = mock[Game]
      when(game.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(game.isPlayerRegistered(gameOwner)) thenReturn(true)
      when(game.gameStatus) thenReturn(GameNotStarted)

      verify(game, never()).checkIfGameEnded(any[Stone])
      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.handlePlayerInput(GameInputMove(Position(0,0)), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(msg => {
          msg shouldBe GameServerMessage("Game not started yet", "invalid-move")
        })
      } yield succeed
    }
    "Handle player move when game already ended" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      val otherPlayer = mock[Player]
      when(otherPlayer.id) thenReturn("other-player-id")

      val game = mock[Game]
      when(game.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(game.isPlayerRegistered(gameOwner)) thenReturn(true)
      when(game.isPlayerRegistered(otherPlayer)) thenReturn(true)
      when(game.gameStatus) thenReturn(GameEnded)

      verify(game, never()).checkIfGameEnded(any[Stone])
      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.handlePlayerInput(GameInputMove(Position(0,0)), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(msg => {
          msg shouldBe GameServerMessage("Game has ended", "invalid-move")
        })
        otherPlayerQ <- data._1.getOrElse(otherPlayer.id, emptyQ).pure[IO]
        _ <- otherPlayerQ.tryDequeue1.asserting(msg => {
          msg shouldBe None
        })
      } yield succeed
    }
    "Handle invalid player move" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      val otherPlayer = mock[Player]
      when(otherPlayer.id) thenReturn("other-player-id")

      val game = mock[Game]
      when(game.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(game.move(WhiteStone(), Position(0,0))) thenReturn(Left("Some move error"))
      when(game.isPlayerRegistered(gameOwner)) thenReturn(true)
      when(game.isPlayerRegistered(otherPlayer)) thenReturn(true)
      when(game.gameStatus) thenReturn(GameInProgress)

      verify(game, never()).checkIfGameEnded(any[Stone])
      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.handlePlayerInput(GameInputMove(Position(0,0)), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(msg => {
          msg shouldBe GameServerMessage("Some move error", "invalid-move")
        })
        otherPlayerQ <- data._1.getOrElse(otherPlayer.id, emptyQ).pure[IO]
        _ <- otherPlayerQ.tryDequeue1.asserting(msg => {
          msg shouldBe None
        })
      } yield succeed
    }
    "Handle valid player move, game not ended after" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      val otherPlayer = mock[Player]
      when(otherPlayer.id) thenReturn("other-player-id")

      val updateGameAfterMove = mock[Game]
      when(updateGameAfterMove.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(updateGameAfterMove.checkIfGameEnded(any[Stone])) thenReturn(false)

      val game = mock[Game]
      when(game.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(game.move(WhiteStone(), Position(0,0))) thenReturn(Right(updateGameAfterMove))
      when(game.isPlayerRegistered(gameOwner)) thenReturn(true)
      when(game.isPlayerRegistered(otherPlayer)) thenReturn(true)
      when(game.gameStatus) thenReturn(GameInProgress)

      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.handlePlayerInput(GameInputMove(Position(0,0)), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(_ shouldBe PlayerMoved(gameOwner, updateGameAfterMove))
        _ <- gameOwnerQ.tryDequeue1.asserting(_ shouldBe None)
        otherPlayerQ <- data._1.getOrElse(otherPlayer.id, emptyQ).pure[IO]
        _ <- otherPlayerQ.dequeue1.asserting(_ shouldBe PlayerMoved(gameOwner, updateGameAfterMove))
        _ <- otherPlayerQ.tryDequeue1.asserting(_ shouldBe None)

      } yield succeed
    }
    "Handle valid player move, game ends afterwards" in {
      val gameOwner = mock[Player]
      when(gameOwner.id) thenReturn("owner-id")

      val otherPlayer = mock[Player]
      when(otherPlayer.id) thenReturn("other-player-id")

      val updateGameAfterMove = mock[Game]
      when(updateGameAfterMove.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(updateGameAfterMove.checkIfGameEnded(any[Stone])) thenReturn(true)

      val game = mock[Game]
      when(game.getPlayerStone(gameOwner.id)) thenReturn(WhiteStone().some)
      when(game.move(WhiteStone(), Position(0,0))) thenReturn(Right(updateGameAfterMove))
      when(game.isPlayerRegistered(gameOwner)) thenReturn(true)
      when(game.isPlayerRegistered(otherPlayer)) thenReturn(true)
      when(game.gameStatus) thenReturn(GameInProgress)

      for {
        emptyQ <- InspectableQueue.unbounded[IO, GameOutputCommand]
        gm <- GameManager[IO](game)
        _ <- gm.registerPlayerForGame(gameOwner)
        _ <- gm.registerPlayerForGame(otherPlayer)
        _ <- gm.handlePlayerInput(GameInputMove(Position(0,0)), gameOwner)
        data <- gm.data.get
        gameOwnerQ <- data._1.getOrElse(gameOwner.id, emptyQ).pure[IO]
        _ <- gameOwnerQ.dequeue1.asserting(_ shouldBe PlayerMoved(gameOwner, updateGameAfterMove))
        _ <- gameOwnerQ.dequeue1.asserting(_ shouldBe GameServerMessage("Game ended", "game-end"))
        otherPlayerQ <- data._1.getOrElse(otherPlayer.id, emptyQ).pure[IO]
        _ <- otherPlayerQ.dequeue1.asserting(_ shouldBe PlayerMoved(gameOwner, updateGameAfterMove))
        _ <- otherPlayerQ.dequeue1.asserting(_ shouldBe GameServerMessage("Game ended", "game-end"))

      } yield succeed
    }
  }
}
