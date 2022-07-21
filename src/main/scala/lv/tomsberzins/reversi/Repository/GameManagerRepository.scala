package lv.tomsberzins.reversi.Repository

import cats.data.EitherT
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import lv.tomsberzins.reversi.domain.GameManager.GameId
import lv.tomsberzins.reversi.domain.{Game, GameEnded, GameManager}

case class GameManagerRepository[F[_] : Concurrent](gameManagers: Ref[F, Map[GameId, GameManager[F]]])
{

  def getAllGames: F[List[Game]] = {
    for {
      gms <- gameManagers.get
      listOfGames <- gms.values.map(_.getGame).toList.sequence
    } yield listOfGames
  }

  def tryCreateGameManagerForGame(createdGame: Game): F[Either[String, GameManager[F]]] = {
    val gmT = for {
      allGames <- EitherT.right(getAllGames)
      _ <- EitherT.fromOption[F](
        allGames.find(existingGame => {
          existingGame.createdBy == createdGame.createdBy
        }).fold(Some(createdGame): Option[Game])(_ => None), "You already have an active game")
      gm <- EitherT.right(GameManager(createdGame))
      modified <- EitherT.right[String](gameManagers.modify(games => {
        (games + (createdGame.id -> gm), gm)
      }))
    } yield modified
    gmT.value
  }

  def getGameManager(gameId: GameId): F[Either[String, GameManager[F]]] = {
    val gmOpt = for {
      gameManagers <- EitherT.liftF(gameManagers.get)
      gm <- EitherT.fromOption[F](gameManagers.get(gameId), "Game with such id not found")
      _ <- EitherT(gm.getGame.map(game => {
        game.gameStatus match {
          case GameEnded => "Game has ended".asLeft
          case _ => game.asRight
        }
      }))

    } yield gm
    gmOpt.value
  }

  def deleteGame(gameId: GameId): F[Unit] = {
    gameManagers.update(gameManagers => {
      gameManagers.removed(gameId)
    })
  }
}

object GameManagerRepository {
  def apply[F[_] : Concurrent]: F[GameManagerRepository[F]] = {
    Ref.of[F, Map[GameId, GameManager[F]]](Map.empty).map(new GameManagerRepository(_))
  }
}
