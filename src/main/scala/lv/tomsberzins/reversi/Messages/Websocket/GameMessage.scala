package lv.tomsberzins.reversi.Messages.Websocket

import cats.implicits.catsSyntaxEitherId
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import lv.tomsberzins.reversi.domain.Game.encodePlayerToStoneMap
import lv.tomsberzins.reversi.domain.{Game, Player, Position, Stone}
object GameMessage {

  sealed trait GameInputMessage extends Message

  case class GameInputMove private (position: Position, action: String) extends GameInputMessage

  object GameInputMove {
    val action = "move"
    def apply(position: Position): GameInputMove = {
      GameInputMove(position, action)
    }
  }

  case class GameInputPlayerLeft private (player: Player, action: String) extends GameInputMessage with GameOutputMessage
  object GameInputPlayerLeft {
    val action = "player-left"
    def apply(player: Player): GameInputPlayerLeft = {
      GameInputPlayerLeft(player, action)
    }
  }

  case class Invalid private (action: String) extends GameInputMessage
  object Invalid {
    val action = "invalid-input"
    def apply(): Invalid = {
      Invalid(action)
    }
  }
  object GameInputMessage {
    implicit val decodeFoo: Decoder[GameInputMessage] = (c: HCursor) =>
      for {
        action <- c.downField("action").as[String]
        inputDecoded <- action match {
          case GameInputMove.action       => c.as[GameInputMove]
          case GameInputPlayerLeft.action => c.as[GameInputPlayerLeft]
          case _ =>
            DecodingFailure("Action not recognized", List())
              .asLeft[GameInputMessage]
        }
      } yield inputDecoded
  }

  sealed trait GameOutputMessage extends Message


  case class GameServerMessage(message: String, action: String) extends GameOutputMessage

  case class PlayerJoined(player: Player) extends GameOutputMessage {
    override val action: String = "player-joined"
  }
  case class PlayerNextToMove(playerNextToMove: Player, game: Game) extends GameOutputMessage {
    override val action: String = "player-next-to-move"
  }

  case class PlayerMoved(playerWhoMoved: Player, game: Game) extends GameOutputMessage {
    override val action: String = "player-moved"
  }

  object GameOutputMessage {

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

    implicit val encodeGameOutputMessage: Encoder[GameOutputMessage] = {
      case GameInputPlayerLeft(player, action) =>
        Json.obj(
          ("player", player.asJson),
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
          ("game",
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
          ("game",
            Json.obj(
              ("board", game.gameState.board.asJson),
              ("player_to_stone", game.playerToStoneMap.asJson)
            )
          )
        )
    }
  }
}
