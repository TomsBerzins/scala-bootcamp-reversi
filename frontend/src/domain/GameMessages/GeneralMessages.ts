abstract class GeneralMessage {
    message: string;

    constructor(message: string) {
        this.message = message;
    }
  }



class GameStarted extends GeneralMessage {
    static action = "game-started";
}

class InvalidMove extends GeneralMessage {
    static action = "invalid-move";
}

class WaitingForOpponent extends GeneralMessage {
    static action = "game-waiting-for-opponent";
}

class GameEnded extends GeneralMessage {
    static action = "game-end";
}

export {GameStarted}
export {InvalidMove}
export {WaitingForOpponent}
export {GameEnded}