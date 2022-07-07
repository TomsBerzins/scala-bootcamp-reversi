package lv.tomsberzins.reversi
import cats.data.OptionT
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import fs2.Pipe
import fs2.concurrent.{Queue, Topic}
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import lv.tomsberzins.reversi.Messages.Http.LobbyRequests.CreatePlayer
import lv.tomsberzins.reversi.Messages.Websocket._
import lv.tomsberzins.reversi.Repository.{GameRepository, PlayerRepository, PlayersInLobbyRepository}
import lv.tomsberzins.reversi.domain.{Game, GameState, Player}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.{HttpRoutes, Response}
object ReversiRoutes {


  def lobbyRoutes[F[_] : Concurrent]
  (
    playerRepository: PlayerRepository[F],
    gamesRepository: GameRepository[F],
    lobbyTopic: Topic[F, OutputCommand],
    playersInLobbyRepo: PlayersInLobbyRepository[F]
  ): HttpRoutes[F] = {

    def uuid: F[String] = Sync[F].delay(java.util.UUID.randomUUID.toString)

    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] {
      case req@POST -> Root / "create-player" =>
        (for {
          createRequest <- req.as[CreatePlayer]
          randomId <- uuid
          player = Player(name = createRequest.nickname, id = randomId)
          newPlayer = playerRepository.createPlayer(player)
        } yield Ok(newPlayer)).flatten

      case GET -> Root / "list-players-in-lobby" => Ok(playersInLobbyRepo.getAllPlayers)
      case GET -> Root / "list-games" => Ok(gamesRepository.getAllGames)

      case GET -> Root / "lobby" / playerId => {
        val toClientPipe: Pipe[F, OutputCommand, WebSocketFrame] = _.map(lobbyOutputMsg => {
          Text(lobbyOutputMsg.asJson.noSpaces)
        })

        def fromClientPipe(player: Player, privateMessageQueue: Queue[F, WebSocketFrame]): Pipe[F, WebSocketFrame, OutputCommand] = {
            _.collect {
              case Text(text, _) => decode[InputCommand](text).getOrElse(Invalid())
              case Close(_) => PlayerLeftLobby(player)
            }
              .evalMap[F, Either[LobbyError, OutputCommand]] {
                case createGameInput@CreateGameInput(name) => for {
                  gameId <- uuid
                  game = Game(gameId, name, player, List(player), GameState())
                  gameCreated <- gamesRepository.createGameIfPossible(player, game)
                  gameOutputMessage <- gameCreated.fold(
                    error => LobbyError(createGameInput, error).asLeft.pure[F],
                    createdGame => {CreateGameOutput(player, createdGame.players, createdGame.id, createdGame.name).asRight.pure[F]}
                  )
                } yield gameOutputMessage
                case playerLeft @ PlayerLeftLobby(player, _) => for {
                    _ <- playersInLobbyRepo.removePlayer(player)
                  } yield playerLeft.asInstanceOf[OutputCommand].asRight[LobbyError]

                case invalid@Invalid(_) => LobbyError(invalid, "Invalid input command").asLeft[OutputCommand].pure[F]
                case ChatInput(message) => ChatOutput(message, player).asInstanceOf[OutputCommand].asRight[LobbyError].pure[F]
              }

              .evalTap {
                case Left(error) => {
                  implicit val encodePerson: Encoder[LobbyError] = deriveEncoder //TODO without this causes stack overflow, recheck
                  privateMessageQueue.enqueue1(Text(error.asJson.noSpaces))
                }
                case Right(_) => ().pure[F]
              }
              .collect {
                case Right(value) => value
              }
        }

        val res = for {
          player <- OptionT(playerRepository.getPlayerById(playerId))
          privateMessageQueue <- OptionT.liftF(Queue.unbounded[F, WebSocketFrame])
          response <- OptionT.liftF(WebSocketBuilder[F].build(
            send =  (lobbyTopic.subscribe(100) through toClientPipe) merge privateMessageQueue.dequeue,
            receive = fromClientPipe(player, privateMessageQueue) andThen lobbyTopic.publish
          ))
          _ <- OptionT.liftF(lobbyTopic.publish1(PlayerJoinedLobby(player)))
          _ <- OptionT.liftF(playersInLobbyRepo.addPlayer(player))
        } yield response

        for {
          res <- res.value
        } yield res match {
          case Some(response) => response
          case _ => Response[F](BadRequest)
        }
      }
    }
  }
}