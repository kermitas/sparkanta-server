package as.sparkanta.message.api

import java.io.Externalizable

class Hello(var deviceId: String) extends Externalizable with HelloExternalization
