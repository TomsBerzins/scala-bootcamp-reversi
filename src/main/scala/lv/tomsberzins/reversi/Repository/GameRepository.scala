package lv.tomsberzins.reversi.Repository

import cats.Monad
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import lv.tomsberzins.reversi.domain.{Game, Player}

abstract class GameRepository[F[_]] {
  def createGame(game: Game): F[Game]

  def getAllGames: F[Map[String, Game]]

  def createGameIfPossible(player: Player, game: Game): F[Either[String, Game]]

}

final class GameRepositoryInMemory[F[_] : Monad](gamesInMem: Ref[F, Map[String, Game]]) extends GameRepository[F] {
  override def createGame(game: Game): F[Game] = {
    gamesInMem.modify(existingGames => {
      val mapEntry = Map(game.id -> game)
      (existingGames ++ mapEntry, game)
    })

  }

  override def getAllGames: F[Map[String, Game]] = {
    gamesInMem.get
  }

  def getGameByNameOrOwner(name: String, owner: Player): F[Option[Game]] = {
    for {
      games <- gamesInMem.get
      exists = games.find({
        case (_, game) => game.name == name || game.createdBy == owner
      })
    } yield {
      if (exists.isDefined) {
        Some(exists.get._2)
      } else {
        None
      }
    }
  }

  override def createGameIfPossible(player: Player, game: Game): F[Either[String, Game]] = {

    val res = for {
      o1 <- getGameByNameOrOwner(game.name, player)
    } yield {
      o1.toLeft(())
    }

    for {
      result <- res
      res2 <- result.fold(_ => "Game with such name exists or you already have an active game".asLeft.pure[F], _ => createGame(game).map(_.asRight))
    } yield res2

  }

}

object GameRepositoryInMemory {

  def apply[F[_] : Sync]: F[GameRepositoryInMemory[F]] = {
    Ref.of[F, Map[String, Game]](Map.empty).map(new GameRepositoryInMemory(_))
  }
}