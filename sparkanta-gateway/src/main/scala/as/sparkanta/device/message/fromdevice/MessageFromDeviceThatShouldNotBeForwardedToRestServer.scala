package as.sparkanta.device.message.fromdevice

/**
 * Marker trait. It defined that this message should not be forwarded to REST server (encapsulated in [[as.sparkanta.gateway.message.fromdevice.MessageFromDevice]]).
 */
trait MessageFromDeviceThatShouldNotBeForwardedToRestServer extends MessageFromDevice