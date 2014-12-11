package as.sparkanta.device.message

/**
 * Marker trait. It defined that this message should not be forwarded to REST server (encapsulated in [[as.sparkanta.gateway.message.MessageFromDevice]]).
 */
trait DoNotForwardToRestServer extends Serializable