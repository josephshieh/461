import java.net.InetAddress;


public class Tor61NodeInfo {
	InetAddress address;
	int port;
	String serviceData;

	public Tor61NodeInfo(InetAddress address, int port, String serviceData) {
		this.address = address;
		this.port = port;
		this.serviceData = serviceData;
	}

	@Override
	public int hashCode() {
		return address.toString().hashCode() + port * 17 + serviceData.hashCode();
	}

	@Override
	public boolean equals(Object o){
		if (this == o) {
			return true;
		}
		if(!(o instanceof Tor61NodeInfo)) {
			return false;
		}
		Tor61NodeInfo nodeInfo = (Tor61NodeInfo) o;
		return (this.address.equals(nodeInfo.address) &&
				this.port == nodeInfo.port && this.serviceData.equals(nodeInfo.serviceData));

	}
}
