package lv.tomsberzins.reversi

import cats.data.EitherT
import cats.effect.Concurrent
import cats.implicits._
import fs2.Pipe
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import lv.tomsberzins.reversi.Messages.Websocket.GameMessage.GameInputMessage._
import lv.tomsberzins.reversi.Messages.Websocket.GameMessage._
import lv.tomsberzins.reversi.Repository._
import lv.tomsberzins.reversi.domain.{GameManager, Player}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}

object ReversiRoutes {
  def reversiRoutes[F[_]: Concurrent](
    gamesManagerContainer: GameManagerRepository[F],
    playerRepository: PlayerRepository[F],
  ): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "ws" / "game" / gameId / playerId =>
      val toClientPipe: Pipe[F, GameOutputMessage, WebSocketFrame] = _.map { lobbyOutputMsg =>
        Text(lobbyOutputMsg.asJson.noSpaces)
      }

      def fromClientPipe(gameManager: GameManager[F], player: Player): Pipe[F, WebSocketFrame, Unit] =
        _.collect {
          case Text(text, _) => decode[GameInputMessage](text).getOrElse(Invalid())
          case Close(_)      => GameInputPlayerLeft(player)
        }.evalMap(gameManager.handlePlayerInput(_, player))

      val responseT = for {
        player <- EitherT.fromOptionF(playerRepository.getPlayerById(playerId), "Player with such id not found")
        gm <- EitherT(gamesManagerContainer.getGameManager(gameId))
        (privateQ, _) <- EitherT(gm.registerPlayerForGame(player))
        response <- EitherT.right(
          WebSocketBuilder[F].build(
            send = privateQ.dequeue through toClientPipe,
            receive = fromClientPipe(gm, player),
          ),
        )
        _ <- EitherT.right(gm.publishToBothPlayers(PlayerJoined(player)))
        _ <- EitherT.right[String](gm.publishGameStateMessage(player.id))
      } yield response

      for {
        res <- responseT.value
        response <- res match {
          case Right(response) => response.pure[F]
          case Left(error)     => BadRequest(error)
        }
      } yield response
    }
  }
}
