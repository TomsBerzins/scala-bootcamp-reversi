import { Expose, Type } from "class-transformer";
import GameBoard from "../Game/GameBoard";
import Player from "./../Player";

class PlayerNextToMove {

    static action = "player-next-to-move";

    @Expose({name:'player_next_to_move'})
    player: Player;

    @Type(() => GameBoard)
    @Expose({name:'game'})
    gameBoard: GameBoard
    
    constructor(player: Player, gameBoard: GameBoard) {
      this.player = player;
      this.gameBoard = gameBoard;
    }
  }

  export default PlayerNextToMove