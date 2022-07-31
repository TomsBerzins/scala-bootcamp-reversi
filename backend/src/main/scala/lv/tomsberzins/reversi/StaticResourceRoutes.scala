package lv.tomsberzins.reversi

import cats.effect.{Blocker, ContextShift, Sync}
import org.http4s._
import org.http4s.dsl.Http4sDsl


object StaticResourceRoutes{

  def routes[F[_] : ContextShift : Sync](blocker: Blocker): HttpRoutes[F] = {

    val dsl = new Http4sDsl[F] {}
    import dsl._

    def static(file: String, blocker: Blocker, request: Request[F]): F[Response[F]] = {
      println(file)
      StaticFile.fromFile(new File("frontend/build/" + file), blocker, Some(request)).getOrElseF(NotFound())
    }

    HttpRoutes.of[F] {

      case request @ GET -> Root / "static"/ "js"/ path=> static("static/js/" + path, blocker, request)

      case request @ GET -> Root / "static"/ "css"/ path=> static("static/css/" + path, blocker, request)

      case request @  _ => static("index.html", blocker, request)
    }
  }
}
