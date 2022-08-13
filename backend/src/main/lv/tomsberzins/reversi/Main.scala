package lv.tomsberzins.reversi

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    ReversiServer.stream[IO]
}
