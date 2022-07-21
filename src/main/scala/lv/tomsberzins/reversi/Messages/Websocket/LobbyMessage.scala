package lv.tomsberzins.reversi.Messages.Websocket

import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json}
import lv.tomsberzins.reversi.domain.Player

sealed trait Command {
  val action: String
}

sealed trait InputCommand extends Command

case class CreateGameInput(name: String) extends InputCommand {
  override val action: String = CreateGameInput.action
}
object CreateGameInput {
  val action = "create-game"
}

case class ChatInput(message: String) extends InputCommand {
  override val action: String = ChatInput.action
}
object ChatInput {
  val action = "chat"
}

case class Invalid(action: String = "invalid-command") extends InputCommand

object InputCommand {
  implicit val decodeLobbyInputCommand: Decoder[InputCommand] = (c: HCursor) => {
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

case class PlayerLeftLobby(player: Player) extends OutputCommand with InputCommand
{
  override val action: String = "player-left-lobby"
}

case class PlayerJoinedLobby(player: Player) extends OutputCommand
{
  override val action: String = "player-joined-lobby"
}

case class CreateGameOutput(owner: Player, players: List[Player], gameId: String, name: String) extends OutputCommand
{
  override val action: String = "game-created"
}

final case class ServerMessage(message: String) extends OutputCommand
{
  override val action: String = "server-message"
}

case class ChatOutput(message: String, sender: Player) extends  OutputCommand
{
  override val action: String = "chat"
}

object OutputCommand {

  implicit val encodeLobbyOutputCommand: Encoder[OutputCommand] = Encoder.instance {
    case output @ CreateGameOutput(_, _, _, _) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)
    case output @ ServerMessage(_) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)
    case output @ PlayerJoinedLobby(_) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)
    case output @ PlayerLeftLobby(_) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)
    case output @ ChatOutput(_, _) => output.asJson deepMerge Json.obj("action" -> output.action.asJson)
  }
}

case class LobbyError(message: String)

object LobbyError {
   def apply(message: String): LobbyError = LobbyError(message)

  implicit val encodeLobbyError: Encoder[LobbyError] = Encoder.instance {
    case bar @ LobbyError(_) => bar.asJson
  }
}

