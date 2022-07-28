package lv.tomsberzins.reversi

import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import fs2.concurrent.Topic
import lv.tomsberzins.reversi.Messages.Websocket.{OutputCommand, ServerMessage}
import lv.tomsberzins.reversi.Repository._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, Logger}

import scala.concurrent.ExecutionContext


object ReversiServer {

  def stream[F[_]: ConcurrentEffect : Timer]: F[ExitCode] = {

    for {
      playersRepository <- PlayerRepositoryInMemory[F]
      lobbyTopic <- Topic[F, OutputCommand](ServerMessage("Welcome")) //TODO try upgrading to fs2 v3
      lobbyPlayers <- PlayersInLobbyRepositoryInMem[F]
      gameManagerContainer <- GameManagerRepository[F]
      httpApp = (
        LobbyRoutes.lobbyRoutes[F](playersRepository, gameManagerContainer, lobbyTopic, lobbyPlayers) <+>
        ReversiRoutes.reversiRoutes[F](gameManagerContainer, playersRepository)
      ).orNotFound

      finalHttpApp = Logger.httpApp(true, true)(httpApp)

      exitCode <- BlazeServerBuilder[F](ExecutionContext.global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(
          CORS.policy.withAllowOriginAll(finalHttpApp)
        )
        .serve.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}
