import Player from "./../Player";
import { Expose } from 'class-transformer';

class PlayerJoinedLobby {

    static action = "player-joined-lobby";

    player: Player;

    @Expose({ name: 'players-in-lobby' })
    playersInLobby: Player[];
    
    constructor(player: Player, playersInLobby: Player[]) {
      this.player = player;
      this.playersInLobby = playersInLobby;

    }
  }

  export default PlayerJoinedLobby