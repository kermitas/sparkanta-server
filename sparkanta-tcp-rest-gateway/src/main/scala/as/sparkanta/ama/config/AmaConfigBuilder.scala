package as.sparkanta.ama.config

import com.typesafe.config.Config
import akka.actor.ActorRef
import as.ama.startup.{ AmaConfig => AmaConfigSpec, AmaConfigBuilder => AmaConfigBuilderSpec }

/**
 * Test runtime properties builder
 */
class AmaConfigBuilder extends AmaConfigBuilderSpec {
  override def createAmaConfig(clazzName: String, commandLineArguments: Array[String], config: Config,
                               broadcaster: ActorRef, initializationResultListener: ActorRef, amaRootActor: ActorRef): AmaConfigSpec = {
    new AmaConfig(commandLineArguments, config, broadcaster, initializationResultListener, amaRootActor)
  }
}
