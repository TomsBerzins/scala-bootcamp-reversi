package lv.tomsberzins.reversi.Messages.Websocket

import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor}
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
  implicit val decodeLobbyMessage: Decoder[InputCommand] = (c: HCursor) => {
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

case class PlayerLeftLobby(player: Player, action: String = "player-left-lobby") extends OutputCommand with InputCommand

case class PlayerJoinedLobby(player: Player, action: String = "player-joined-lobby") extends OutputCommand

case class CreateGameOutput(owner: Player, players: List[Player], gameId: String, name: String, action: String = "game-created") extends OutputCommand

final case class ServerMessage(message: String, action: String = "server-message") extends OutputCommand

case class ChatOutput(message: String, sender: Player, action: String = "chat") extends  OutputCommand

object OutputCommand {

  implicit val encodeLobbyMessage: Encoder[OutputCommand] = Encoder.instance {
    case output @ CreateGameOutput(_, _, _, _, _) => output.asJson
    case output @ ServerMessage(_,_) => output.asJson
    case output @ PlayerJoinedLobby(_,_) => output.asJson
    case output @ PlayerLeftLobby(_,_) => output.asJson
    case output @ ChatOutput(_, _,_) => output.asJson
  }
}

case class LobbyError(action: String, msg: String)

object LobbyError {
   def apply(command: Command, message: String): LobbyError = LobbyError(command.action, message)

  implicit val encodeLobbyError: Encoder[LobbyError] = Encoder.instance {
    case bar @ LobbyError(_,_) => bar.asJson
  }
}

