package lv.tomsberzins.reversi.Repository

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import lv.tomsberzins.reversi.domain.GameManager.GameId
import lv.tomsberzins.reversi.domain.{Game, GameEnded, GameManager}

case class GameManagerRepository[F[_]: Concurrent](
    gameManagers: Ref[F, Map[GameId, GameManager[F]]]
) {

  def getAllGames: F[List[Game]] = {
    for {
      gms <- gameManagers.get
      listOfGames <- gms.values.map(_.getGame).toList.sequence
    } yield listOfGames
  }

  def tryCreateGameManagerForGame(
      createdGame: Game
  ): F[Either[String, GameManager[F]]] = {
    getAllGames
      .map(
        _.find(existingGame => existingGame.createdBy == createdGame.createdBy)
          .fold(Option(createdGame))(_ => None)
      )
      .flatMap { allGames =>
        allGames.toRight("You already have an active game").traverse { _ =>
          GameManager(createdGame).flatMap { gm =>
            gameManagers.modify(games => (games + (createdGame.id -> gm), gm))
          }
        }
      }
  }

  def getGameManager(gameId: GameId): F[Either[String, GameManager[F]]] = {
    gameManagers.get
      .map(gms => {
        gms.get(gameId).toRight("Game with such id not found")
      })
      .flatMap(
        _.fold(
          error => error.asLeft[GameManager[F]].pure[F],
          gm => {
            gm.getGame.map(game => {
              game.gameStatus match {
                case GameEnded => "Game has ended".asLeft
                case _         => gm.asRight
              }
            })
          }
        )
      )
  }

  def deleteGame(gameId: GameId): F[Unit] = {
    gameManagers.update(gameManagers => {
      gameManagers.removed(gameId)
    })
  }
}

object GameManagerRepository {
  def apply[F[_]: Concurrent]: F[GameManagerRepository[F]] = {
    Ref
      .of[F, Map[GameId, GameManager[F]]](Map.empty)
      .map(new GameManagerRepository(_))
  }
}
