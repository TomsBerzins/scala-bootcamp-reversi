package lv.tomsberzins.reversi.domain

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Player private (name: String, id: String)
object Player {
  implicit val playerDecoder: Decoder[Player] = deriveDecoder[Player]
  implicit val playerEncoder: Encoder[Player] = deriveEncoder[Player]

  def apply[F[_]: Sync](name: String): F[Player] = {
    def uuid: F[String] = Sync[F].delay(java.util.UUID.randomUUID.toString)

    uuid.map(new Player(name, _))
  }
}
