package lv.tomsberzins.reversi.Repository

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits.toFunctorOps
import lv.tomsberzins.reversi.domain.Player

trait PlayersInLobbyRepository[F[_]] {
  def addPlayer(player: Player): F[Unit]

  def removePlayer(player: Player): F[Unit]

  def getAllPlayers: F[Map[String, Player]]
}

class PlayersInLobbyRepositoryInMem[F[_]](players: Ref[F, Map[String, Player]]) extends PlayersInLobbyRepository[F] {
  def addPlayer(player: Player): F[Unit] = players.update(_.updated(player.id, player))

  def removePlayer(player: Player): F[Unit] = players.update(_.removed(player.id))

  def getAllPlayers: F[Map[String, Player]] = players.get
}

object PlayersInLobbyRepositoryInMem {
  def apply[F[_] : Concurrent]: F[PlayersInLobbyRepository[F]] = Ref.of[F, Map[String, Player]](Map.empty).map(new PlayersInLobbyRepositoryInMem(_))
}