import Player from "./../Player";

class PlayerLeftGame {

    static action = "player-left";

    player: Player;
    
    constructor(player: Player) {
      this.player = player;
    }
  }

  export default PlayerLeftGame