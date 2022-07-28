package lv.tomsberzins.reversi.Messages.Websocket

import cats.implicits.catsSyntaxEitherId
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import lv.tomsberzins.reversi.domain.Game.encodePlayerToStoneMap
import lv.tomsberzins.reversi.domain.{Game, Player, Position, Stone}
object GameMessage {

  sealed trait Command {
    val action: String
  }

  sealed trait GameInputCommand extends Command

  sealed trait GameOutputCommand extends Command

  case class GameInputMove private (position: Position, action: String) extends GameInputCommand

  object GameInputMove {
    val action = "move"
    def apply(position: Position): GameInputMove = {
      GameInputMove(position, action)
    }
  }

  case class GameInputPlayerLeft private (playerId: String, action: String) extends GameInputCommand with GameOutputCommand
  object GameInputPlayerLeft {
    val action = "player-left"
    def apply(playerId: String): GameInputPlayerLeft = {
      GameInputPlayerLeft(playerId, action)
    }
  }

  case class Invalid private (action: String) extends GameInputCommand
  object Invalid {
    val action = "invalid-input"
    def apply(): Invalid = {
      Invalid(action)
    }
  }
  object GameInputCommand {
    implicit val decodeFoo: Decoder[GameInputCommand] = (c: HCursor) =>
      for {
        action <- c.downField("action").as[String]
        inputDecoded <- action match {
          case GameInputMove.action       => c.as[GameInputMove]
          case GameInputPlayerLeft.action => c.as[GameInputPlayerLeft]
          case _ =>
            DecodingFailure("Action not recognized", List())
              .asLeft[GameInputCommand]
        }
      } yield inputDecoded
  }

  case class GameServerMessage(message: String, action: String) extends GameOutputCommand

  case class PlayerJoined(player: Player) extends GameOutputCommand {
    override val action: String = "player-joined"
  }
  case class PlayerNextToMove(playerNextToMove: Player, game: Game) extends GameOutputCommand {
    override val action: String = "player-next-to-move"
  }

  case class PlayerMoved(playerWhoMoved: Player, game: Game) extends GameOutputCommand {
    override val action: String = "player-moved"
  }

  object GameOutputCommand {

    implicit val encodeGameState: Encoder[Map[Position, Option[Stone]]] =
      (map: Map[Position, Option[Stone]]) => {
        val jsonObjects = for {
          value <- map
        } yield {
          Json.obj(
            ("position", value._1.asJson),
            ("stone", value._2.asJson)
          )
        }

        Json.arr(jsonObjects.toSeq: _*)
      }

    implicit val encodeGameOutputCommand: Encoder[GameOutputCommand] = {
      case GameInputPlayerLeft(playerId, action) =>
        Json.obj(
          ("player_id", Json.fromString(playerId)),
          ("action", Json.fromString(action))
        )
      case GameServerMessage(message, action) =>
        Json.obj(
          ("message", Json.fromString(message)),
          ("action", Json.fromString(action))
        )
      case msg @ PlayerJoined(player) =>
        Json.obj(
          ("player", player.asJson),
          ("action", Json.fromString(msg.action))
        )
      case msg @ PlayerNextToMove(player, game) =>
        Json.obj(
          ("player_next_to_move", player.asJson),
          ("action", Json.fromString(msg.action)),
          (
            "game",
            Json.obj(
              ("board", game.gameState.board.asJson),
              ("player_to_stone", game.playerToStoneMap.asJson)
            )
          )
        )
      case msg @ PlayerMoved(player, game) =>
        Json.obj(
          ("player_who_moved", player.asJson),
          ("action", Json.fromString(msg.action)),
          (
            "game",
            Json.obj(
              ("board", game.gameState.board.asJson),
              ("player_to_stone", game.playerToStoneMap.asJson)
            )
          )
        )
    }
  }
}
