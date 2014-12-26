package as.sparkanta.message

import akka.util.ByteString
import as.sparkanta.gateway.NetworkDeviceInfo

class NewDataFromDevice(val networkDeviceInfo: NetworkDeviceInfo, val dataFromDevice: ByteString) extends Serializable {

  override def toString = s"${getClass.getSimpleName}(dataFromDevice=${dataFromDevice.length} bytes,networkDeviceInfo=$networkDeviceInfo)"

}