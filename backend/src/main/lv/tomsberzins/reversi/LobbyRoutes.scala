package lv.tomsberzins.reversi
import cats.data.OptionT
import cats.effect.Concurrent
import cats.implicits._
import fs2.Pipe
import fs2.concurrent.{Queue, Topic}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import lv.tomsberzins.reversi.Messages.Http.LobbyRequests.CreatePlayer
import lv.tomsberzins.reversi.Messages.Websocket.OutputMessage.encodePlayersInLobby
import lv.tomsberzins.reversi.Messages.Websocket._
import lv.tomsberzins.reversi.Repository.{GameManagerRepository, PlayerRepository, PlayersInLobbyRepository}
import lv.tomsberzins.reversi.domain._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.{HttpRoutes, Response}

object LobbyRoutes {

  def lobbyRoutes[F[_]: Concurrent](
    playerRepository: PlayerRepository[F],
    gamesManagerContainer: GameManagerRepository[F],
    lobbyTopic: Topic[F, OutputMessage],
    playersInLobbyRepo: PlayersInLobbyRepository[F],
  ): HttpRoutes[F] = {

    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req @ POST -> Root / "create-player" =>
        for {
          createRequest <- req.as[CreatePlayer]
          player <- Player(name = createRequest.nickname)
          newPlayer = playerRepository.createPlayer(player)
          response <- Ok(newPlayer)
        } yield response

      case GET -> Root / "list-players-in-lobby" => Ok(playersInLobbyRepo.getAllPlayers.map(_.asJson))

      case GET -> Root / "list-games" => Ok(gamesManagerContainer.getAllActiveGames.map(_.asJson(Game.encodeGameList)))

      case GET -> Root / "ws" / "lobby" / playerId =>
        val toClientPipe: Pipe[F, OutputMessage, WebSocketFrame] = _.map { lobbyOutputMsg =>
          Text(lobbyOutputMsg.asJson(OutputMessage.encodeLobbyOutputMessage).noSpaces)
        }

        def fromClientPipe(
          player: Player,
          privateMessageQueue: Queue[F, WebSocketFrame],
        ): Pipe[F, WebSocketFrame, OutputMessage] =
          _.collect {
            case Text(text, _) => decode[InputMessage](text).getOrElse(Invalid())
            case Close(_)      => PlayerLeftInput(player)
          }
            .evalMap[F, Either[LobbyError, OutputMessage]] {
              case CreateGameInput(name) =>
                for {
                  game <- Game(name, player)
                  gameManagerCreated <- gamesManagerContainer.tryCreateGameManagerForGame(game)
                  allGames <- gamesManagerContainer.getAllActiveGames
                  gameOutputMessage <- gameManagerCreated.fold(
                    error => LobbyError(error).asLeft.pure[F],
                    _     => CreateGameOutput(player, game.playerToStoneMap, game.id, game.name, allGames).asRight.pure[F],
                  )
                } yield gameOutputMessage
              case PlayerLeftInput(player) =>
                for {
                  _ <- playersInLobbyRepo.removePlayer(player)
                  playerList <- playersInLobbyRepo.getAllPlayers
                } yield PlayerLeftOutput(player, playerList).asRight[LobbyError]

              case Invalid(_) => LobbyError("Invalid input message").asLeft[OutputMessage].pure[F]
              case ChatInput(message) =>
                ChatOutput(message, player).asInstanceOf[OutputMessage].asRight[LobbyError].pure[F]
            }
            .evalTap {
              case Left(error) => privateMessageQueue.enqueue1(Text(error.asJson.noSpaces))
              case Right(_)    => ().pure[F]
            }
            .collect { case Right(value) =>
              value
            }

        val res = for {
          player <- OptionT(playerRepository.getPlayerById(playerId))
          privateMessageQueue <- OptionT.liftF(Queue.unbounded[F, WebSocketFrame])
          response <- OptionT.liftF(
            WebSocketBuilder[F].build(
              send = (lobbyTopic.subscribe(100) through toClientPipe) merge privateMessageQueue.dequeue,
              receive = fromClientPipe(player, privateMessageQueue) andThen lobbyTopic.publish,
            ),
          )
          _ <- OptionT.liftF(playersInLobbyRepo.addPlayer(player))
          playerList <- OptionT.liftF(playersInLobbyRepo.getAllPlayers)
          _ <- OptionT.liftF(lobbyTopic.publish1(PlayerJoinedLobby(player, playerList)))
        } yield response

        for {
          res <- res.value
        } yield res match {
          case Some(response) => response
          case _              => Response[F](BadRequest)
        }
    }
  }
}
