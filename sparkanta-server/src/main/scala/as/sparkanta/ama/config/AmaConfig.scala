package as.sparkanta.ama.config

import akka.actor.ActorRef
import as.ama.startup.{ AmaConfig => AmaConfigSpec }
import com.typesafe.config.Config

class AmaConfig(
  commandLineArguments:         Array[String],
  config:                       Config,
  broadcaster:                  ActorRef,
  initializationResultListener: ActorRef,
  amaRootActor:                 ActorRef
) extends AmaConfigSpec(commandLineArguments, config, broadcaster, initializationResultListener, amaRootActor)