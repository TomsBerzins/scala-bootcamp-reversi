
class CreateGameInput {
    private action = "create-game";
    name: string
    constructor(name: string) {
      this.name = name;
    }
  }

  export default CreateGameInput