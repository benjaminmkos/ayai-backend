package ayai.systems

/** Ayai Imports **/
import ayai.apps.Constants
import ayai.networking._
import ayai.components._
import ayai.persistence._
import ayai.gamestate.{Effect, EffectType, GameStateSerializer, MapRequest, RoomWorld, GetRoomJson}
import ayai.factories._

/** Akka Imports **/
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

/** Crane Imports **/
import crane.{Entity, World, TimedSystem}

/** Socko Imports **/
import org.mashupbots.socko.events.WebSocketFrameEvent

/** External Imports **/
import scala.concurrent.{Await, ExecutionContext, Promise}
import scala.concurrent.duration._
import scala.collection.concurrent.{Map => ConcurrentMap}
import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap
import scala.io.Source

import java.rmi.server.UID
import java.lang.Boolean

import net.liftweb.json._
import net.liftweb.json.JsonDSL._

import org.slf4j.{Logger, LoggerFactory}

object NetworkingSystem {
  def apply(networkSystem: ActorSystem) = new NetworkingSystem(networkSystem)
}


class NetworkingSystem(networkSystem: ActorSystem) extends TimedSystem(1000/30) {
  private val log = LoggerFactory.getLogger(getClass)
  implicit val timeout = Timeout(Constants.NETWORK_TIMEOUT seconds)

  override def processTime(delta: Int) {
    val name = world.asInstanceOf[RoomWorld].name
    val serializer = networkSystem.actorSelection(s"user/Serializer$name")

    val entities = world.getEntitiesByComponents(classOf[Character], classOf[NetworkingActor])

    for(characterEntity <- entities) {
      val characterId: String = (characterEntity.getComponent(classOf[Character])) match {
        case Some(c: Character) => c.id 
        case _ =>
          log.warn("8192c19: getComponent failed to return anything")
          ""
      }
      if(!characterEntity.getComponent(classOf[MapChange]).isEmpty) {
        characterEntity.getComponent(classOf[MapChange]) match {
          case Some(map: MapChange) =>
            val future2 = serializer ? new MapRequest(new Entity)
            val result2 = Await.result(future2, timeout.duration).asInstanceOf[String]
            val actorSelection1 = characterEntity.getComponent(classOf[NetworkingActor]) match {
              case Some(na: NetworkingActor) => na
              case _ => null
            }

            println(result2)
            actorSelection1.actor ! new ConnectionWrite(result2)  
            characterEntity.removeComponent(classOf[MapChange])
          case _ =>
            log.warn("990f22d: getComponent failed to return anything")
        }
      }

      //This is how we get character specific info, once we actually integrate this in.
      val future1 = serializer ? GetRoomJson
      val result1 = Await.result(future1, timeout.duration).asInstanceOf[String]
      val actorSelection = characterEntity.getComponent(classOf[NetworkingActor]) match {
            case Some(na : NetworkingActor) => na
            case _ => null
      }
      println(result1)
      actorSelection.actor ! new ConnectionWrite(result1)
    }
  }

  def broadcastMessage(message : String) {
    val actorSelection = networkSystem.actorSelection("user/SockoSender*")
    actorSelection ! new ConnectionWrite(message)
  }
}
