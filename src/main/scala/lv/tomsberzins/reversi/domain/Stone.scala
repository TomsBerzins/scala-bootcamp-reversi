package lv.tomsberzins.reversi.domain

sealed trait Stone {
  def flip(): Stone
}

case class WhiteStone() extends Stone {
  override def flip(): BlackStone = BlackStone()
}

case class BlackStone() extends Stone {
  override def flip(): WhiteStone = WhiteStone()
}