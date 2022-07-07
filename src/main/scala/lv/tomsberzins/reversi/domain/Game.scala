package lv.tomsberzins.reversi.domain


final case class GameState()

final case class Game (id: String, name: String = "aa", createdBy: Player, players: List[Player], gameState: GameState) {

}
