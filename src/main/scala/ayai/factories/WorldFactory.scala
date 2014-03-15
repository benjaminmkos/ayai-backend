package ayai.factories

import ayai.apps.Constants
import ayai.systems._
import ayai.gamestate.{RoomWorld, GameStateSerializer, MessageProcessorSupervisor, TileMap}
import akka.actor.{ActorSystem, Props}

object WorldFactory {
  def apply(networkSystem: ActorSystem) = new WorldFactory(networkSystem)
}

class WorldFactory(networkSystem: ActorSystem) {
  def createWorld(name: String, file: String): RoomWorld = {
    val tileMap = EntityFactory.loadRoomFromJson(s"$file.json")
    var world: RoomWorld = RoomWorld(name, tileMap)

    world.addSystem(MovementSystem(networkSystem))
    world.addSystem(HealthSystem())
    world.addSystem(RespawningSystem())
    world.addSystem(FrameExpirationSystem())
    world.addSystem(DirectorSystem())
    world.addSystem(RoomChangingSystem(networkSystem))
    world.addSystem(NetworkingSystem(networkSystem))
    world.addSystem(CollisionSystem(networkSystem))
    world.addSystem(AttackSystem(networkSystem))
    val serializer = networkSystem.actorOf(Props(GameStateSerializer(world)), s"Serializer$name")
    val nmProcessor = networkSystem.actorOf(Props(MessageProcessorSupervisor(world)), name=s"MProcessor$name")

    val entity = EntityFactory.createAI(world)
    world.addEntity(entity)

    world
  }
}
