import { Type } from "class-transformer";
import Game from "./../Game/Game"
import { Expose } from 'class-transformer';

class CreateGameOutput {
  
  static action = "game-created";

  @Expose({ name: 'created-game' })
  createdGame: Game;

  @Type(() => Game)
  @Expose({ name: 'games'})
  gameList: Game[];

  constructor(createdGame: Game, gameList: Game[]) {
    this.createdGame = createdGame;
    this.gameList = gameList;
  }
}


export default CreateGameOutput