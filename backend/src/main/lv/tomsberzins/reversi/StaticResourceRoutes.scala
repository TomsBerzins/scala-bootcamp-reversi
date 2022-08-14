package lv.tomsberzins.reversi

import cats.effect.{Blocker, ContextShift, Sync}
import cats.implicits.toSemigroupKOps
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.staticcontent.resourceServiceBuilder

object StaticResourceRoutes {

  def routes[F[_]: ContextShift: Sync](blocker: Blocker): HttpRoutes[F] = {

    val dsl = new Http4sDsl[F] {}
    import dsl._

    val catchAllReturnIndex = HttpRoutes.of[F] { case _ =>
      StaticFile.fromResource("webapp/index.html", blocker).getOrElseF(NotFound())
    }

    val routesFromFiles = resourceServiceBuilder[F]("/webapp", blocker).toRoutes

    routesFromFiles <+> catchAllReturnIndex
  }
}
