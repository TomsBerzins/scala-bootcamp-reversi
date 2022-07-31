import { Type } from "class-transformer";
import Player from "./../Player"
class Game {
    owner: Player;
    id: string;

    @Type(() => PlayerStoneMap)
    players: PlayerStoneMap[];
    
    name: string
    constructor(owner: Player, id: string, name: string, players: PlayerStoneMap[]) {
      this.owner = owner;
      this.id = id;
      this.players = players;
      this.name = name;
    }
  }

class PlayerStoneMap {
    player: Player;
    stone: string;

    constructor(player: Player, stone: string) {
        this.player = player;
        this.stone = stone;
      }
}  


  export {PlayerStoneMap}
  export default Game