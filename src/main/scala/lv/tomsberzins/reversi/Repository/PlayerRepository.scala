package lv.tomsberzins.reversi.Repository

import cats.Monad
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import lv.tomsberzins.reversi.domain.Player

trait PlayerRepository[F[_]] {
  def getPlayerById(id: String): F[Option[Player]]
  def getAllPlayers(): F[Map[String, Player]]
  def createPlayer(player: Player): F[Player]
}

final class PlayerRepositoryInMemory[F[_]: Monad] (playersInMem: Ref[F, Map[String, Player]]) extends PlayerRepository[F] {

  override def getPlayerById(id: String): F[Option[Player]] = {
    for {
      players <- playersInMem.get
    } yield players.get(id)
  }

  override def getAllPlayers(): F[Map[String, Player]] = {
    playersInMem.get
  }

  override def createPlayer(player: Player): F[Player] = {
    playersInMem.modify(existingPlayers => {
      val mapEntry = Map(player.id -> player)
      (existingPlayers ++ mapEntry, player)
    })
  }
}

object PlayerRepositoryInMemory {

  def apply[F[_]: Sync]: F[PlayerRepositoryInMemory[F]] = {
    Ref.of[F, Map[String, Player]](Map.empty).map(new PlayerRepositoryInMemory(_))
  }
}