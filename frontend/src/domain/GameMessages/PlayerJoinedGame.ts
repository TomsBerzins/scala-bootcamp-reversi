import Player from "./../Player";

class PlayerJoinedGame {

    static action = "player-joined";

    player: Player;
    
    constructor(player: Player) {
      this.player = player;
    }
  }

  export default PlayerJoinedGame