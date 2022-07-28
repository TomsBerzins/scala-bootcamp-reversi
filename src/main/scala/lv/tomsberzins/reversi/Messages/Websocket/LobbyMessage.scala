package lv.tomsberzins.reversi.Messages.Websocket

import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import lv.tomsberzins.reversi.domain.Game.encodePlayerToStoneMap
import lv.tomsberzins.reversi.domain._

sealed trait InputCommand extends Command

case class CreateGameInput(name: String) extends InputCommand {
  override val action: String = CreateGameInput.action
}
object CreateGameInput {
  val action = "create-game"
}

case class PlayerLeftInput(player: Player) extends InputCommand {
  override val action: String = "player-left-lobby"
}
object PlayerLeftInput {
  val action = "player-left-lobby"
}

case class ChatInput(message: String) extends InputCommand {
  override val action: String = ChatInput.action
}
object ChatInput {
  val action = "chat"
}

case class Invalid(action: String = "invalid-command") extends InputCommand

object InputCommand {
  implicit val decodeLobbyInputCommand: Decoder[InputCommand] = (c: HCursor) =>
    {
      for {
        action <- c.downField("action").as[String]
      } yield action match {
        case CreateGameInput.action => {
          (for {
            name <- c.downField("name").as[String]
          } yield CreateGameInput(name)).getOrElse(Invalid())
        }
        case ChatInput.action =>
          (for {
            name <- c.downField("message").as[String]
          } yield ChatInput(name)).getOrElse(Invalid())

        case unknownAction => Invalid(unknownAction)
      }
    }
}

sealed trait OutputCommand extends Command

case class PlayerLeftOutput(player: Player, playersInLobby: Map[String, Player])
    extends OutputCommand {
  override val action: String = "player-left-lobby"
}

case class PlayerJoinedLobby(
    player: Player,
    playersInLobby: Map[String, Player]
) extends OutputCommand {
  override val action: String = "player-joined-lobby"
}

case class CreateGameOutput(
    owner: Player,
    players: Map[Player, Stone],
    gameId: String,
    name: String,
    games: List[Game]
) extends OutputCommand {
  override val action: String = "game-created"
}

case class ServerMessage(message: String) extends OutputCommand {
  override val action: String = "server-message"
}

case class ChatOutput(message: String, sender: Player) extends OutputCommand {
  override val action: String = "chat"
}

object OutputCommand {

  implicit val encodeLobbyOutputCommand: Encoder[OutputCommand] = {
    case output @ CreateGameOutput(owner, players, gameId, name, games) =>
      val outputJson = Json.obj(
        (
          "created-game",
          Json.obj(
            ("owner", owner.asJson),
            ("players", players.asJson),
            ("id", gameId.asJson),
            ("name", name.asJson)
          )
        ),
        ("games", games.asJson(Game.encodeGameList))
      )
      outputJson.asJson deepMerge Json.obj("action" -> output.action.asJson)

    case output @ ServerMessage(_) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)

    case output @ PlayerJoinedLobby(player, playersInLobby) => Json.obj(
        ("action", Json.fromString(output.action)),
        ("player", player.asJson),
        ("players-in-lobby", playersInLobby.asJson)
      )

    case output @ PlayerLeftOutput(player, playersInLobby) => Json.obj(
        ("action", Json.fromString(output.action)),
        ("player", player.asJson),
        ("players-in-lobby", playersInLobby.asJson)
      )
    case output @ ChatOutput(_, _) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)

  }

  implicit val encodePlayersInLobby: Encoder[Map[String, Player]] =
    (map: Map[String, Player]) => {
      val jsonObjects = for {
        value <- map
      } yield {
        Json.obj(
          ("id", value._1.asJson),
          ("name", value._2.name.asJson)
        )
      }
      Json.arr(jsonObjects.toSeq: _*)
    }

}

case class LobbyError(message: String)

object LobbyError {
  implicit val encodeLobbyError: Encoder[LobbyError] = lobbyError => {
    Json.obj(
      ("message", Json.fromString(lobbyError.message))
    )
  }
}
