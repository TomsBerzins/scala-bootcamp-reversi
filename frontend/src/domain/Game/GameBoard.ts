import { Type } from "class-transformer";
import { PlayerStoneMap } from "./Game"
import { Expose } from 'class-transformer';

class Position {
    x: number;
    y: number;

    constructor(x: number, y: number) {
        this.x = x;
        this.y =y;
    }
}



class GameBoard {
  
    @Type(() => Tile)
    board: Tile[];

    @Expose({ name: 'player_to_stone' })
    @Type(() => PlayerStoneMap)
    playerToStoneMap: PlayerStoneMap[];

  constructor(board: Tile[], playerToStoneMap: PlayerStoneMap[]) {
    this.board = board;
    this.playerToStoneMap = playerToStoneMap;
  }
}

class Tile {
  position: Position;
  stone?: string|null;

  constructor(position: Position, stone: string|null) {
      this.position = position;
      this.stone = stone;
  }
}


export default GameBoard
export {Position}
export {Tile}
