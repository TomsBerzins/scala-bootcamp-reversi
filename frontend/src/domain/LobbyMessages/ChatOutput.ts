import Player from "./../Player"
class ChatOutputMessage {
    static action = "chat";
    sender: Player;
    message: string
    constructor(sender: Player, message: string) {
      this.sender = sender;
      this.message = message;
    }
  }


  export default ChatOutputMessage