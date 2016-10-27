package twl

import akka.actor.{ActorSystem, Props}


object Main extends App {
  val system = ActorSystem("twl")
  system.actorOf(Props[Server], "server")
}












