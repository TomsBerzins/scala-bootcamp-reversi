package lv.tomsberzins.reversi

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits.catsSyntaxOptionId
import lv.tomsberzins.reversi.domain.GameState.{Board, attemptMove}
import lv.tomsberzins.reversi.domain._
import org.scalatest.EitherValues
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatestplus.mockito.MockitoSugar.mock

class GameSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers with EitherValues {

  val player = mock[Player]

  def testGame = Game[IO]("someName", player)

  def createEmptyBoard(): GameState = {
    val stones = for {
      x <- 0 until 8
      y <- 0 until 8
    } yield {
      (Position(x, y) -> Option.empty[Stone])
    }

    GameState(stones.toMap, BlackStone())
  }

  "Game creation behaves correctly" - {
    "only game name and owner needed to create game" in   {
      testGame.asserting(p => {
        p.name shouldBe "someName"
        p.createdBy shouldBe player
      })
    }
    "random game id should be generated" in {
      testGame.asserting(p => {
        p.id.length shouldBe 36
      })
    }
    "player who created game should be set as the first player to move (black stone)" in {
      testGame.asserting(p => {
        p.playerToStoneMap.size shouldBe 1
        p.playerToStoneMap.values.head shouldBe BlackStone()
      })
    }
    "game status should be 'not started'" in {
      testGame.asserting(p => {
        p.gameStatus shouldBe GameNotStarted
      })
    }
  }

  "Game in progress predicate" in {
    testGame.asserting(g => {
      val gameNotStarted = g.copy(gameStatus = GameNotStarted)
      val gameEnded = g.copy(gameStatus = GameEnded)
      val gameInProgress = g.copy(gameStatus = GameInProgress)

      assertResult(false) {
        gameNotStarted.inProgress
      }
      assertResult(false) {
        gameEnded.inProgress
      }
      assertResult(true) {
        gameInProgress.inProgress
      }
    })
  }

  "Get next player to move" - {
    "Game created, owner should have the first move" in {
      testGame.asserting(game => {
        game.getPlayerNextToMove.get shouldBe player
      })
    }
    "Change gamestate next move to be other player, game should reflect that" in {
      testGame.asserting(game => {
        val otherPlayer = mock[Player]
        val gameWithOtherPlayer = game.copy(playerToStoneMap = game.playerToStoneMap.updated(otherPlayer, WhiteStone()))
        val updatedGameState = gameWithOtherPlayer.copy(gameState = game.gameState.copy(nextStone = WhiteStone()))

        updatedGameState.getPlayerNextToMove.get shouldBe otherPlayer
      })
    }
  }
  "Get players stone" - {
    "valid player id" in {
      for {
        player <- Player[IO]("player1")
        game <- Game[IO]("name", player)
      } yield {
        game.getPlayerStone(player.id).get shouldBe BlackStone()
      }
    }
    "providing invalid id" in {
      testGame.asserting(game => {
        game.getPlayerStone("doesnt_exist") shouldBe None
      })
    }
  }

  "Check player is registered to play the game" - {
    "game owner should be registered" in {
      testGame.asserting(game => {
        game.isPlayerRegistered(player) shouldBe true
      })
    }
    "Newly created game, check some other user if registered" in {
      Player[IO]("someName").flatMap(player => {
        testGame.asserting(game => {
          game.isPlayerRegistered(player) shouldBe false
        })
      })
    }
  }

  "Check if game is full" - {
    "Newly created game should no be full" in {
      testGame.asserting(game => {
        game.isFull shouldBe false
      })
    }
    "Game with two players should be full" in {
      for {
        otherPlayer <- Player[IO]("player1")
        game <- testGame
      } yield {
        val gameWithAdditionalPlayer = game.copy(playerToStoneMap = game.playerToStoneMap.updated(otherPlayer, WhiteStone()))
        gameWithAdditionalPlayer.isFull shouldBe true
      }
    }
  }
  "Game move" - {

    val gameWithTwoPlayers = for {
      game <- testGame
      otherPlayer <- Player[IO]("player2")
    } yield game.copy(playerToStoneMap = game.playerToStoneMap.updated(otherPlayer, WhiteStone()))
    "Newly created game, white stone player should not be allowed to move first" in {
      gameWithTwoPlayers.asserting(game => {
        game.move(WhiteStone(), Position(0,0)).left.value shouldBe "Not your move"
      })
      gameWithTwoPlayers.asserting(game => {
        game.move(BlackStone(), Position(3, 2)) match {
          case Left(_) => fail("this should be a valid move")
          case Right(_) => succeed
        }
      })
    }
    "Try to make a move out of board bounds" in {
      gameWithTwoPlayers.asserting(game => {
        game.move(BlackStone(), Position(8,8)).left.value shouldBe "Move is out of bounds"
        game.move(BlackStone(), Position(0,8)).left.value shouldBe "Move is out of bounds"
        game.move(BlackStone(), Position(8,0)).left.value shouldBe "Move is out of bounds"
        game.move(BlackStone(), Position(-1,-1)).left.value shouldBe "Move is out of bounds"
      })
    }
    "Try to place a stone in already occupied position" in {
      gameWithTwoPlayers.asserting(game => {
        game.move(BlackStone(), Position(3,3)).left.value shouldBe "This position is already occupied"
      })
    }

    /**
            Starting board
            0 1 2 3 4 5 6 7
          0 . . . . . . . .
          1 . . . . . . . .
          2 . . . . . . . .
          3 . . . W B . . .
          4 . . . B W . . .
          5 . . . . . . . .
          6 . . . . . . . .
          7 . . . . . . . .
     */
     val startingBoard= GameState.resetBoard().board
    /**
          Diagonal testing board
            0 1 2 3 4 5 6 7
          0 . . . . . . . .
          1 . . . . . . . .
          2 . . . . . . . .
          3 . . . W B . . .
          4 . . . W B . . .
          5 . . . W B . . .
          6 . . . . . . . .
          7 . . . . . . . .
     */
    val diagonalTestingBoard = GameState.resetBoard().board +
      (Position(3,3) -> WhiteStone().some) +
      (Position(3,4) -> WhiteStone().some) +
      (Position(3,5) -> WhiteStone().some) +
      (Position(4,3) -> BlackStone().some) +
      (Position(4,4) -> BlackStone().some) +
      (Position(4,5) -> BlackStone().some)
    val moveAndExpectedBoard = Table(
      ("move", "currentBoard", "expectedBoard"),
      (Move(Position(5,4),BlackStone()), startingBoard, createEmptyBoard().board + //test left horizontal
        (Position(3,3) -> WhiteStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(5,4) -> BlackStone().some)
      ),
      (Move(Position(2,3),BlackStone()), startingBoard, createEmptyBoard().board + //test right horizontal
        (Position(3,3) -> BlackStone().some) +
        (Position(2,3) -> BlackStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> WhiteStone().some)
      ),
      (Move(Position(4,5), BlackStone()), startingBoard, createEmptyBoard().board + //vertical up
        (Position(3,3) -> WhiteStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(4,5) -> BlackStone().some)
      ),
      (Move(Position(3,2), BlackStone()), startingBoard, createEmptyBoard().board + //vertical down
        (Position(3,3) -> BlackStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> WhiteStone().some) +
        (Position(3,2) -> BlackStone().some)
      ),
      (Move(Position(2,2), BlackStone()), diagonalTestingBoard, createEmptyBoard().board + //diag down right
        (Position(2,2) -> BlackStone().some) +
        (Position(3,3) -> BlackStone().some) +
        (Position(3,4) -> WhiteStone().some) +
        (Position(3,5) -> WhiteStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(4,5) -> BlackStone().some)
      ),
      (Move(Position(2,6), BlackStone()), diagonalTestingBoard, createEmptyBoard().board + //diag up right
        (Position(2,6) -> BlackStone().some) +
        (Position(3,3) -> WhiteStone().some) +
        (Position(3,4) -> WhiteStone().some) +
        (Position(3,5) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(4,5) -> BlackStone().some)
      ),
        (Move(Position(5,2), WhiteStone()), diagonalTestingBoard, createEmptyBoard().board + //diag down left
          (Position(3,3) -> WhiteStone().some) +
          (Position(3,4) -> WhiteStone().some) +
          (Position(3,5) -> WhiteStone().some) +
          (Position(4,3) -> WhiteStone().some) +
          (Position(4,4) -> BlackStone().some) +
          (Position(4,5) -> BlackStone().some) +
          (Position(5,2) -> WhiteStone().some)
        ),
      (Move(Position(5,6), WhiteStone()), diagonalTestingBoard, createEmptyBoard().board + //diag up left
        (Position(3,3) -> WhiteStone().some) +
        (Position(3,4) -> WhiteStone().some) +
        (Position(3,5) -> WhiteStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(4,5) -> WhiteStone().some) +
        (Position(5,6) -> WhiteStone().some)
      )
    )

    "Make valid moves and flip one stone in every way" in {

      forAll(moveAndExpectedBoard){(move: Move, startingBoard: Board, expectedBoard: Board) => {

        val gameState = GameState(startingBoard, move.stone)
        attemptMove(move, gameState).value.board shouldBe expectedBoard
      }
      }
    }
    "Make a perfect move (all stones in one color) in shortest way possible" in {

      /**
            Starting board
            0 1 2 3 4 5 6 7            0 1 2 3 4 5 6 7
          0 . . . . . . . .          0 . . . . . . . .
          1 . . . . . . . .          1 . . . . . . . .
          2 . . . . B . . .          2 . . . . B . . .
          3 . . . B W B . .   --->   3 . . . B B B . .
          4 . . B B W B B .          4 . . B B B B B .
          5 . . . W W W . .          5 . . . B B B . .
          6 . . . . . . . .          6 . . . . B . . .
          7 . . . . . . . .          7 . . . . . . . .
       */
      val startingBoard = createEmptyBoard().board +
        (Position(4,2) -> BlackStone().some) +
        (Position(3,3) -> BlackStone().some) +
        (Position(4,3) -> WhiteStone().some) +
        (Position(5,3) -> BlackStone().some) +
        (Position(2,4) -> BlackStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,4) -> WhiteStone().some) +
        (Position(5,4) -> BlackStone().some) +
        (Position(6,4) -> BlackStone().some) +
        (Position(3,5) -> WhiteStone().some) +
        (Position(4,5) -> WhiteStone().some) +
        (Position(5,5) -> WhiteStone().some)

      val expectedBoard = createEmptyBoard().board +
        (Position(4,2) -> BlackStone().some) +
        (Position(3,3) -> BlackStone().some) +
        (Position(4,3) -> BlackStone().some) +
        (Position(5,3) -> BlackStone().some) +
        (Position(2,4) -> BlackStone().some) +
        (Position(3,4) -> BlackStone().some) +
        (Position(4,4) -> BlackStone().some) +
        (Position(5,4) -> BlackStone().some) +
        (Position(6,4) -> BlackStone().some) +
        (Position(3,5) -> BlackStone().some) +
        (Position(4,5) -> BlackStone().some) +
        (Position(5,5) -> BlackStone().some) +
        (Position(4,6) -> BlackStone().some)

      val gameState = GameState(startingBoard, BlackStone())
      attemptMove(Move(Position(4,6), BlackStone()), gameState).value.board shouldBe expectedBoard
    }
    "Make invalid moves where mo stones are being flipped" in {
      val moveAndBoardWhereNoStonesShouldBeFlipped = Table(
        ("move", "board"),
        (Move(Position(5,2), WhiteStone()), startingBoard),
        (Move(Position(0,0), WhiteStone()), startingBoard),
        (Move(Position(2,2), BlackStone()), startingBoard)
      )

      forAll(moveAndBoardWhereNoStonesShouldBeFlipped){(move: Move, board: Board) => {
        val gameState = GameState(board, move.stone)
        attemptMove(move,gameState).left.value shouldBe "Invalid move, no stones flipped"
      }}
    }
    "Check if game ended" - {
      "Game should end when board is not full and all stones are of the same color" in {
        /**

            0 1 2 3 4 5 6 7
          0 . . . . . . . .
          1 . . . . . . . .
          2 . . . . B . . .
          3 . . . B B B . .
          4 . . B B B B B .
          5 . . . B B B . .
          6 . . . . B . . .
          7 . . . . . . . .
         */

        val gameEndBoard = createEmptyBoard().board +
          (Position(4,2) -> BlackStone().some) +
          (Position(3,3) -> BlackStone().some) +
          (Position(4,3) -> BlackStone().some) +
          (Position(5,3) -> BlackStone().some) +
          (Position(2,4) -> BlackStone().some) +
          (Position(3,4) -> BlackStone().some) +
          (Position(4,4) -> BlackStone().some) +
          (Position(5,4) -> BlackStone().some) +
          (Position(6,4) -> BlackStone().some) +
          (Position(3,5) -> BlackStone().some) +
          (Position(4,5) -> BlackStone().some) +
          (Position(5,5) -> BlackStone().some) +
          (Position(4,6) -> BlackStone().some)
        val game = testGame.map(g => g.copy(gameState = GameState(gameEndBoard, WhiteStone())))

        game.asserting(game => {
          game.checkIfGameEnded(WhiteStone()) shouldBe true
        })
      }
      "Game should end when board is full" in {
        val fullBoardWithWhiteAndBlackStones = for {
          x <- 0 until 8
          y <- 0 until 8
        } yield {
          if (x  % 2 == 0) {
            (Position(x, y) -> BlackStone().some)
          } else {
            (Position(x, y) -> WhiteStone().some)
          }
        }
        val gameState = GameState(fullBoardWithWhiteAndBlackStones.toMap, BlackStone())
        val game = testGame.map(game => {
          game.copy(gameState = gameState)
        })

        game.asserting(game => {
          game.checkIfGameEnded(BlackStone()) shouldBe true
        })
      }
      "Newly created game, should not end" in {
        testGame.asserting(game => {
          game.checkIfGameEnded(BlackStone()) shouldBe false
        })
      }
    }
  }

}