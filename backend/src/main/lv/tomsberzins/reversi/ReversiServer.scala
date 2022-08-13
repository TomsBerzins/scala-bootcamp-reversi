package lv.tomsberzins.reversi

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, Timer}
import cats.syntax.all._
import fs2.concurrent.Topic
import lv.tomsberzins.reversi.Messages.Websocket.{OutputMessage, ServerMessage}
import lv.tomsberzins.reversi.Repository._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.CORS

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext


object ReversiServer {

  def stream[F[_]: ConcurrentEffect : Timer : ContextShift]: F[ExitCode] = {

    for {

      playersRepository <- PlayerRepositoryInMemory[F]
      lobbyTopic <- Topic[F, OutputMessage](ServerMessage("Welcome")) //TODO try upgrading to fs2 v3 so empty topic can be created
      lobbyPlayers <- PlayersInLobbyRepositoryInMem[F]
      gameManagerContainer <- GameManagerRepository[F]
      blockingEc = Blocker.liftExecutorService(Executors.newFixedThreadPool(4))
      httpApp = (
        LobbyRoutes.lobbyRoutes[F](playersRepository, gameManagerContainer, lobbyTopic, lobbyPlayers) <+>
        ReversiRoutes.reversiRoutes[F](gameManagerContainer, playersRepository) <+>
          StaticResourceRoutes.routes(blockingEc)
      ).orNotFound

      exitCode <- BlazeServerBuilder[F](ExecutionContext.global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(
          CORS.policy.withAllowOriginAll(httpApp)
        )
        .serve.compile.drain.as(ExitCode.Success)
    } yield exitCode
  }
}
