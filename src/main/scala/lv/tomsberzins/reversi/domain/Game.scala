package lv.tomsberzins.reversi.domain

import cats.effect.Sync
import cats.implicits._
import io.circe.Encoder
import lv.tomsberzins.reversi.domain.GameManager.PlayerId
import lv.tomsberzins.reversi.domain.GameState.{Board, getFreePositions, resetBoard}

import scala.annotation.tailrec

sealed trait GameStatus

case object GameNotStarted extends GameStatus
case object GameInProgress extends GameStatus
case object GameEnded extends GameStatus

case class Position(x: Int, y: Int)

case class Move(position: Position, stone: Stone)

case class GameState(board: Map[Position, Option[Stone]], nextStone: Stone) {

  def printPretty(): String = { //TODO temporary for debugging
    var b = ""
    val bb = Array.ofDim[Position](8, 8)
    for {
      x <- 0 until 8
      y <- 0 until 8
    } yield {
      bb(y)(x) = Position(x, y)
    }


    bb foreach { row => {
      row foreach (a => {


        val rr = board.getOrElse(a, None)
        rr match {
          case Some(f) => f match {
            case WhiteStone() => b = b + "| W "
            case BlackStone() => b = b + "| B "
          }
          case _ => b = b + "|   "
        }
      })
      b = b + '|'
      b = b + '\n'
    }
    }
    b

  }
}

object GameState {
  type Board = Map[Position, Option[Stone]]
  def attemptMove(toBePlaced: Move, gameState: GameState): Either[String, GameState] = {
    val stoneBeingPlaced = toBePlaced.stone

    @tailrec
    def getFlippableCount(board: Board, position: Position, howToCalculateNextPosition: Position => Position, amountFlipped: Int = 0): Int = {
      val positionBeingFlipped = howToCalculateNextPosition(position)
      val enclosingPositionOfFlipped = howToCalculateNextPosition(positionBeingFlipped)

      board.get(positionBeingFlipped) match {
        case Some(Some(stone)) =>
          if (stone == stoneBeingPlaced) {
          amountFlipped
        } else {
          board.get(enclosingPositionOfFlipped) match {
            case Some(Some(stone)) => {
              if (stoneBeingPlaced == stone) {
                amountFlipped + 1
              } else {
                getFlippableCount(board, positionBeingFlipped,howToCalculateNextPosition, amountFlipped + 1)
              }
            }
            case _ => 0
          }
        }
        case _ => amountFlipped
      }
    }

    @tailrec
    def flipStones(board: GameState.Board, position: Position, amountToFlip: Int, howToCalculateNextPosition: Position => Position): Board = {
      if (amountToFlip <= 0) {
        board
      } else {
        val positionToBeFlipped = howToCalculateNextPosition(position)
        board.get(positionToBeFlipped) match {
          case Some(Some(stone)) => {
            val updatedBoard = board.updated(positionToBeFlipped, stone.flip().some)
            flipStones(updatedBoard, positionToBeFlipped, amountToFlip -1, howToCalculateNextPosition)
          }
          case _ => board
        }
      }
    }

    @tailrec
    def flipIfPossible(board: Board, toBePlaced: Position, howToCalculateNextPositions: List[Position => Position]): Board = {
      howToCalculateNextPositions match {
        case ::(howToCalculateNextPosition, rest) => {
          val flippableCount = getFlippableCount(board, toBePlaced, howToCalculateNextPosition)
          if (flippableCount > 0) {
            val newBoard = flipStones(board, toBePlaced, flippableCount, howToCalculateNextPosition)
            flipIfPossible(newBoard, toBePlaced, rest)
          } else {
            flipIfPossible(board, toBePlaced, rest)
          }
        }
        case Nil => board
      }
    }

    val flipHorizontalRight = (pos: Position) => pos.copy(x = pos.x + 1)
    val flippedHorizontalLeft = (pos: Position) => pos.copy(x = pos.x - 1)
    val flippedVerticalUp = (pos: Position) => pos.copy(y = pos.y - 1)
    val flippedVerticalDown = (pos: Position) => pos.copy(y = pos.y +1)
    val flippedDiagUpRight = (pos: Position) => pos.copy(x = pos.x + 1, y= pos.y - 1)
    val flippedDiagUpLeft = (pos: Position) => pos.copy(x = pos.x -1, y = pos.y - 1)
    val flippedDiagDownRight = (pos: Position) => pos.copy(x = pos.x + 1, y = pos.y + 1)
    val flippedDiagDownLeft = (pos: Position) => pos.copy(x = pos.x -1, y = pos.y + 1)

    lazy val newBoard = flipIfPossible(gameState.board, toBePlaced.position,List(
      flipHorizontalRight,
      flippedHorizontalLeft,
      flippedVerticalUp,
      flippedVerticalDown,
      flippedDiagUpRight,
      flippedDiagUpLeft,
      flippedDiagDownRight,
      flippedDiagDownLeft,
    ))

    if (stoneBeingPlaced == gameState.nextStone) {
      if (!isPositionInBounds(toBePlaced.position)) {
        "Move is out of bounds".asLeft[GameState]
      } else if (!isPositionFree(toBePlaced.position, gameState)) {
        "This position is already occupied".asLeft[GameState]
      } else {
        if (newBoard.equals(gameState.board)) {
          "Invalid move, no stones flipped".asLeft[GameState]
        } else {
          // if move is valid place the stone
          val withStonePlaced = newBoard.updated(toBePlaced.position, stoneBeingPlaced.some)
          gameState.copy(withStonePlaced, stoneBeingPlaced.flip()).asRight[String]
        }
      }
    } else {
      "Not your move".asLeft[GameState]
    }


  }

  def isPositionInBounds(position: Position): Boolean = {
    position.y >= 0 && position.y <= 7 && position.x >= 0 && position.x <= 7
  }

  def isPositionFree(position: Position, gameState: GameState): Boolean = {
    gameState.board.get(position) match {
      case Some(Some(_)) => false
      case _ => true
    }
  }

  def resetBoard(): GameState = {
    val stones = for {
      x <- 0 until 8
      y <- 0 until 8
    } yield {
      val stone = (x, y) match {
        case (3, 3) => WhiteStone().some
        case (3, 4) => BlackStone().some
        case (4, 3) => BlackStone().some
        case (4, 4) => WhiteStone().some
        case _ => Option.empty[Stone]
      }
      (Position(x, y) -> stone)
    }

    GameState(stones.toMap, BlackStone())
  }

  def getFreePositions(board: Board): List[Position] = {
    board.filter(pair => {
      pair._2 match {
        case Some(_) => false
        case None => true
      }
    }).keys.toList
  }
}


case class Game private(
                 id: String,
                 name: String,
                 playerToStoneMap: Map[Player, Stone],
                 createdBy: Player,
                 gameState: GameState,
                 gameStatus: GameStatus
               ) {

  def inProgress: Boolean = {
    gameStatus == GameInProgress
  }

  def getPlayerNextToMove: Option[Player] = {
    val nextStoneToMove = this.gameState.nextStone
    this.playerToStoneMap.find(_._2 == nextStoneToMove)._1F
  }

  def getPlayerStone(playerId: PlayerId): Option[Stone] = {
    this.playerToStoneMap.find(_._1.id == playerId)._2F
  }

  def isPlayerRegistered(player: Player): Boolean = {
    playerToStoneMap.isDefinedAt(player)
  }

  def isFull: Boolean = {
    playerToStoneMap.size >= 2
  }

  def move(stone: Stone, position: Position): Either[String, Game] = {
    for {
      updatedGameState <- GameState.attemptMove(Move(position, stone), gameState)
    } yield this.copy(gameState = updatedGameState)
  }

  def checkIfGameEnded(nextStone: Stone): Boolean = {
    @tailrec
    def check(freePositions: List[Position], board: Board): Boolean = { // TODO actually need to check only bordering outer fields of existing stones
      freePositions match {
        case ::(position, next) => {
          GameState.attemptMove(Move(position, nextStone),gameState) match {
            case Left(_) => check(next, board)
            case Right(_) => false
          }
        }
        case Nil => true
      }
    }

    check(getFreePositions(gameState.board), gameState.board)
  }
}
object Game {
  implicit val encodeFoo: Encoder[Game] = {
    Encoder.forProduct2("id", "name")(game => (game.id, game.name))
  }

  def apply[F[_]: Sync](name: String, owner: Player): F[Game] = {
    def uuid: F[String] = Sync[F].delay(java.util.UUID.randomUUID.toString)
    for {
      id <- uuid
    } yield new Game(id, name, Map(owner -> BlackStone()),owner, resetBoard(), GameNotStarted)
  }
}
