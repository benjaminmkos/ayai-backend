package ayai.apps

/** Ayai Imports **/
import ayai.systems._
import ayai.networking._
import ayai.components._
import ayai.persistence._
import ayai.gamestate.{Effect, EffectType, GameStateSerializer, CharacterRadius, MapRequest}
import ayai.factories._

/** Akka Imports **/
import akka.actor.{Actor, ActorRef, ActorSystem, Status, Props}
import akka.actor.Status.{Success, Failure}
import akka.pattern.ask
import akka.util.Timeout

/** Crane Imports **/
import crane.{Entity, World}

/** Socko Imports **/
import org.mashupbots.socko.events.WebSocketFrameEvent

/** External Imports **/
import scala.concurrent.{Await, ExecutionContext, Promise, Future}
import scala.concurrent.duration._
import scala.collection.concurrent.{Map => ConcurrentMap}
import scala.collection.JavaConversions._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.io.Source

import java.rmi.server.UID
import java.lang.Boolean

import net.liftweb.json._
import net.liftweb.json.JsonDSL._

import org.slf4j.{Logger, LoggerFactory}


object GameLoop {
  var roomHash: HashMap[Long, Entity] = HashMap.empty[Long, Entity]
  private val log = LoggerFactory.getLogger(getClass)

  var running: Boolean = true

  def main(args: Array[String]) {
    DBCreation.ensureDbExists()

    var socketMap: ConcurrentMap[String, String] = new java.util.concurrent.ConcurrentHashMap[String, String]
    var worlds: HashMap[String, World]()
    var world: World = new World()

    world.createGroup("ROOMS")    
    world.createGroup("CHARACTERS")
    var room: Entity = EntityFactory.loadRoomFromJson(world, Constants.STARTING_ROOM_ID, "map3.json")
    roomHash.put(Constants.STARTING_ROOM_ID, room)
    world.createGroup("ROOM"+Constants.STARTING_ROOM_ID)
    world.addEntity(room)

    room = EntityFactory.loadRoomFromJson(world, 1, "map2.json")
    roomHash.put(1, room)
    world.createGroup("ROOM"+1)
    world.addEntity(room)

    ItemFactory.bootup(world)
    ClassFactory.bootup(world)

    //world.addSystem(MovementSystem(roomHash))
    //world.addSystem(RoomChangingSystem(roomHash))
    //world.addSystem(HealthSystem())
    //world.addSystem(RespawningSystem())
    //world.addSystem(FrameExpirationSystem())
    //world.initialize()
    
    //load all rooms


    implicit val timeout = Timeout(Constants.NETWORK_TIMEOUT seconds)
    import ExecutionContext.Implicits.global

    val networkSystem = ActorSystem("NetworkSystem")
    val messageQueue = networkSystem.actorOf(Props[NetworkMessageQueue], name="NMQueue")
    val interpreter = networkSystem.actorOf(Props[NetworkMessageInterpreterSupervisor], name="NMInterpreter")
    val messageProcessor = networkSystem.actorOf(Props(NetworkMessageProcessorSupervisor(world, socketMap)), name="NMProcessor")
    val authorization = networkSystem.actorOf(Props[AuthorizationProcessor], name="AProcessor")

    val serializer = networkSystem.actorOf(Props(new GameStateSerializer(world, Constants.LOAD_RADIUS)))

    world.addSystem(new NetworkingSystem(networkSystem, serializer, roomHash))
    world.addSystem(new CollisionSystem(networkSystem))

    val receptionist = new SockoServer(networkSystem)
    receptionist.run(Constants.SERVER_PORT)

    //GAME LOOP RUNS AS LONG AS SERVER IS UP
    while(running) {
      //get the time 
      val start = System.currentTimeMillis

      val future = messageQueue ? new FlushMessages() // enabled by the “ask” import
      val result = Await.result(future, timeout.duration).asInstanceOf[QueuedMessages]

      val processedMessages = new ArrayBuffer[Future[Any]]
      result.messages.foreach { message =>
        processedMessages += messageProcessor ? new ProcessMessage(message)
      }
      
      Await.result(Future.sequence(processedMessages), 1 seconds)

      world.process()

      val end = System.currentTimeMillis
      if((end - start) < (1000/Constants.FRAMES_PER_SECOND)) {
        Thread.sleep((1000 / Constants.FRAMES_PER_SECOND) - (end-start))
      }
    }
  }
}


