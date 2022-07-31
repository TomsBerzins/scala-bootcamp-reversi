package lv.tomsberzins.reversi.domain

import io.circe.Encoder
import io.circe.syntax.EncoderOps

sealed trait Stone {
  def flip(): Stone
}

case class WhiteStone() extends Stone {
  override def flip(): BlackStone = BlackStone()
}

case class BlackStone() extends Stone {
  override def flip(): WhiteStone = WhiteStone()
}

object Stone {
  implicit val encodeStone: Encoder[Stone] = {
    case WhiteStone() => "white_stone".asJson
    case BlackStone() => "black_stone".asJson
  }
}