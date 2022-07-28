package lv.tomsberzins.reversi.domain

import cats.Applicative
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.concurrent.InspectableQueue
import lv.tomsberzins.reversi.Messages.Websocket.GameMessage._
import lv.tomsberzins.reversi.domain.GameManager.PlayerId

/** Each active game is represented by a game manager which is exposes api to interact with the game
  * and players (via map of queues, similar to how topic is implemented but without the 1 message retention limit)
  */
case class GameManager[F[_]: Concurrent](
    data: Ref[F, (Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)]
) {
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

  def removeQueueForPlayer(
      playerId: PlayerId
  ): F[(Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)] = {
    data.updateAndGet(pair => {
      (pair._1.removed(playerId), pair._2)
    })
  }

  def publishToBothPlayers(msg: GameOutputCommand): F[Unit] = {
    def publishToPlayers(
        playerQueues: List[InspectableQueue[F, GameOutputCommand]],
        msg: GameOutputCommand
    ): F[Unit] = {
      playerQueues match {
        case ::(queue, next) =>
          queue.enqueue1(msg) *> publishToPlayers(next, msg)
        case Nil => ().pure[F]
      }
    }

    data.get.flatMap(data => {
      publishToPlayers(data._1.values.toList, msg)
    })
  }

  def publishToSpecificPlayer(
      playerId: PlayerId,
      msg: GameOutputCommand
  ): F[Unit] = {
    for {
      cData <- data.get
      _ <- cData._1.get(playerId) match {
        case Some(q) => q.enqueue1(msg)
        case _       => ().pure[F]
      }
    } yield ()
  }

  def handlePlayerInput(
      gameInputCommand: GameInputCommand,
      player: Player
  ): F[Unit] = {
    gameInputCommand match {
      case GameInputMove(position, _) =>
        val moveNotPossibleOrStone = getGame.map(game => {
          val gameInProgress = game.gameStatus match {
            case GameNotStarted => "Game not started yet".asLeft
            case GameEnded      => "Game has ended".asLeft
            case GameInProgress => game.asRight
          }
          val playerStoneFound =
            game.getPlayerStone(player.id).toRight("Player not found")

          Applicative[Either[String, *]].map2(gameInProgress, playerStoneFound)(
            (_, stone) => stone
          )
        })

        moveNotPossibleOrStone.flatMap( _.fold(
          moveNotPossibleError =>
            publishToSpecificPlayer(
              player.id,
              GameServerMessage(moveNotPossibleError, "invalid-move")
            ),
          stone => {
            data.modify(pair => {
                val game = pair._2
                game.move(stone, position) match {
                  case Left(error) => (pair, error.asLeft[Game])
                  case Right(game) => ((pair._1, game), game.asRight[String])
                }
              })
              .flatMap(
                _.fold(
                  invalidMoveError =>
                    publishToSpecificPlayer(
                      player.id,
                      GameServerMessage(invalidMoveError, "invalid-move")
                    ),
                  game => {
                    if (game.checkIfGameEnded(stone.flip())) {
                      setGameEnded() *> publishToBothPlayers(PlayerMoved(player, game)) *> publishToBothPlayers(GameServerMessage("Game ended", "game-end"))
                    } else {
                      publishToBothPlayers(PlayerMoved(player, game))
                    }
                  }
                )
              )
          }
        ))
      case GameInputPlayerLeft(player, _) => removeQueueForPlayer(player.id) *> publishToBothPlayers(GameInputPlayerLeft(player))
      case Invalid(action) => publishToSpecificPlayer(player.id, GameServerMessage("Invalid input", action))
    }
  }

  /**
   * Used when player joins(also rejoins later on) to get latest game state
   */
  def publishGameStateMessage(playerId: PlayerId): F[Unit] = {
    for {
      bothPlayersOnline <- bothPlayersOnline()
      game <- getGame
      _ <- game.getPlayerNextToMove match {
        case Some(playerToMove) =>
          if (bothPlayersOnline && !game.inProgress) {
            this.setGameInProgress() *>
              publishToBothPlayers(GameServerMessage("Game started", "game-started")) *> publishToBothPlayers(PlayerNextToMove(playerToMove, game))
          } else if (!game.inProgress) {
            publishToSpecificPlayer(playerId, GameServerMessage("Waiting for other player to join", "game-waiting-for-opponent"))
          } else {
            publishToSpecificPlayer(playerId, PlayerNextToMove(playerToMove, game))
          }
        case None => ().pure[F] //this shouldn't happen
      }

    } yield ()
  }

  def registerPlayerForGame(
      player: Player
  ): F[Either[String, (InspectableQueue[F, GameOutputCommand], Game)]] = {
    for {
      newQueue <- InspectableQueue.unbounded[F, GameOutputCommand]
      queueAndGame <- data.modify(pair => {
        val game = pair._2
        val queues = pair._1
        if (game.isFull && !game.isPlayerRegistered(player)) {
          (pair, "Game is full".asLeft[(InspectableQueue[F, GameOutputCommand], Game)])
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

  def apply[F[_]: Concurrent](game: Game): F[GameManager[F]] = {
    Ref
      .of[F, (Map[PlayerId, InspectableQueue[F, GameOutputCommand]], Game)](
        (Map.empty, game)
      )
      .map(GameManager(_))
  }
}
