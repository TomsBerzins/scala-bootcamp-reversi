import { Expose, Type } from "class-transformer";
import GameBoard from "../Game/GameBoard";
import Player from "./../Player";

class PlayerMoved {

    static action = "player-moved";

    @Expose({name:'player_who_moved'})
    player: Player;

    @Type(() => GameBoard)
    @Expose({name:'game'})
    gameBoard: GameBoard
    
    constructor(player: Player, gameBoard: GameBoard) {
      this.player = player;
      this.gameBoard = gameBoard;
    }
  }

  export default PlayerMoved