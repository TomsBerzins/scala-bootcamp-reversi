package lv.tomsberzins.reversi.domain

import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.InspectableQueue
import lv.tomsberzins.reversi.Messages.Websocket.GameMessage._
import lv.tomsberzins.reversi.domain.GameManager.PlayerId

case class GameManager[F[_] : Concurrent](data: Ref[F, (Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)]) {
  def bothPlayersOnline(): F[Boolean] = data.get.map(_._1.size == 2)

  def getGame: F[Game] = data.get._2F

  def setGameInProgress(): F[Game] = {
    data.modify(pair => {
      val updatedGame = pair._2.copy(gameStatus = GameInProgress)

      ((pair._1, updatedGame), updatedGame)
    })
  }

  def setGameEnded(): F[Game] = {
    data.modify(pair => {
      val updatedGame = pair._2.copy(gameStatus = GameEnded)

      ((pair._1, updatedGame), updatedGame)
    })
  }

  def removeQueueForPlayer(playerId: PlayerId): F[(Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)] = {
    data.updateAndGet(pair => {
      (pair._1.removed(playerId), pair._2)
    })
  }

  def publishToBothPlayers(msg: GameOutputCommand): F[Unit] = {
    def publishToPlayers(playerQueues: List[InspectableQueue[F, GameOutputCommand]], msg: GameOutputCommand): F[Unit] = {
      playerQueues match {
        case ::(queue, next) => queue.enqueue1(msg) *> publishToPlayers(next, msg)
        case Nil => ().pure[F]
      }
    }

    for {
      cData <- data.get
      _ <- publishToPlayers(cData._1.values.toList, msg)
    } yield ()

  }

  def publishToSpecificPlayer(playerId: PlayerId, msg: GameOutputCommand): F[Unit] = {
    for {
      cData <- data.get
      _ <- cData._1.get(playerId) match {
        case Some(q) => q.enqueue1(msg)
        case _ => ().pure[F]
      }
    } yield ()
  }

  def handlePlayerInput(gameInputCommand: GameInputCommand, player: Player): F[Unit] = {
    gameInputCommand match {
      case GameInputMove(position, _) => {

        val gameUpdatedOrError = for {
          game <- EitherT.liftF(getGame)
          playerStone <- EitherT.fromOption[F](game.getPlayerStone(player.id), "Error")
          updatedGame <- EitherT(data.modify(pair => {
            val game = pair._2
            game.move(playerStone, position) match {
              case Left(error) => (pair, error.asLeft[Game])
              case Right(game) => ((pair._1, game), game.asRight[String])
            }
          }))
        } yield updatedGame

        for {
          moveResult <- gameUpdatedOrError.value
          _ <- moveResult match {
            case Left(error) => publishToSpecificPlayer(player.id, GameServerMessage(error))
            case Right(game) => {
              game.getPlayerStone(player.id) match {
                case Some(playerStone) => {
                  if (game.checkIfGameEnded(playerStone.flip())) {
                    publishToBothPlayers(PlayerMoved(player,game)) *> publishToBothPlayers(GameServerMessage("Game ended")) *> setGameEnded()
                  } else {
                    publishToBothPlayers(PlayerMoved(player,game))
                  }
                }
                case None => ().pure[F]
              }
            }
          }
        } yield ()
      }
      case GameInputPlayerLeft(playerId, _) =>
        removeQueueForPlayer(playerId) *> publishToBothPlayers(GameInputPlayerLeft(playerId))
      case inv@Invalid(_, _) => publishToSpecificPlayer(player.id, inv)
    }
  }

  def publishGameStateMessage(playerId: PlayerId): F[Unit] = {
    for {
      bothPlayersOnline <- bothPlayersOnline()
      game <- getGame
      _ <- game.getPlayerNextToMove match {
        case Some(playerToMove) => {
          if (bothPlayersOnline && !game.inProgress) {
            this.setGameInProgress() *>
              publishToBothPlayers(GameServerMessage("Game started")) *>
              publishToBothPlayers(PlayerNextToMove(playerToMove, game))
          } else if (!game.inProgress) {
            publishToSpecificPlayer(playerId, GameServerMessage("Waiting for other player to join"))
          } else {
            publishToSpecificPlayer(playerId, PlayerNextToMove(playerToMove, game))
          }
        }
        case None => ().pure[F] //this shouldn't happen
      }

    } yield ()
  }

  def registerPlayerForGame(player: Player): F[Either[String, (InspectableQueue[F, GameOutputCommand], Game)]] = {
    for {
      newQueue <- InspectableQueue.unbounded[F, GameOutputCommand]
      queueAndGame <- data.modify(pair => {
        val game = pair._2
        val queues = pair._1
        if (game.isFull && !game.isPlayerRegistered(player)) {
          (pair, "Game is full".asLeft[(InspectableQueue[F, GameOutputCommand] , Game)])
        } else if (game.isPlayerRegistered(player)) {
          val addedQueue = queues.updated(player.id, newQueue)
          val updatedPair = (addedQueue, game)
          (updatedPair, (newQueue, game).asRight)
        } else {
          val addedQueue = queues.updated(player.id, newQueue)
          val existingPlayerStone = game.playerToStoneMap.values.head
          val updatedGame = game.copy(playerToStoneMap = game.playerToStoneMap.updated(player, existingPlayerStone.flip()))
          val updatedPair = (addedQueue, updatedGame)
          (updatedPair, (newQueue, game).asRight)
        }
      })
    } yield queueAndGame
  }
}

object GameManager {

  type GameId = String
  type PlayerId = String

  def apply[F[_] : Concurrent](game: Game): F[GameManager[F]] = {
    Ref.of[F, (Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)]((Map.empty, game)).map(GameManager(_))
  }
}
