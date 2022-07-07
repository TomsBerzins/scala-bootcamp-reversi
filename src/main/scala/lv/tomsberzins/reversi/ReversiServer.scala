package lv.tomsberzins.reversi

import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import fs2.concurrent.Topic
import lv.tomsberzins.reversi.Messages.Websocket.{OutputCommand, ServerMessage}
import lv.tomsberzins.reversi.Repository.{GameRepositoryInMemory, PlayerRepositoryInMemory, PlayersInLobbyRepositoryInMem}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.Logger

import scala.concurrent.ExecutionContext


object ReversiServer {

  def stream[F[_]: ConcurrentEffect : Timer]: F[ExitCode] = {

    for {
      playersRepository <- PlayerRepositoryInMemory[F]
      gamesRepository <- GameRepositoryInMemory[F]
      lobbyTopic <- Topic[F, OutputCommand](ServerMessage("Welcome")) //TODO try upgrading to fs2 v3
      lobbyPlayers <- PlayersInLobbyRepositoryInMem[F]
      httpApp = (
        ReversiRoutes.lobbyRoutes[F](playersRepository, gamesRepository, lobbyTopic, lobbyPlayers)
      ).orNotFound

      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F](ExecutionContext.global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(
          finalHttpApp
        )
        .serve.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}
