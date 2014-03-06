import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

/**
 * Joseph Shieh, 1031718, josephs2@uw.edu
 * Sergey Naumets, 1025573, snaumets@uw.edu
 * CSE 461 Networks Project 3, .
 */

public class Tor61Node {
	String regServerHost;
	int regServerPort;
	int groupNum;
	int instanceNum;
	int httpProxyPort;


	public Tor61Node(String regServerHost, int regServerPort, int groupNum, int instanceNum, int httpProxyPort) {
		this.regServerHost = regServerHost;
		this.regServerPort = regServerPort;
		this.groupNum = groupNum;
		this.instanceNum = instanceNum;
		this.httpProxyPort = httpProxyPort;
		String instanceHex = String.format("%04d", Integer.valueOf(Long.toHexString(instanceNum), 16));
		long serviceData = Long.parseLong(Long.toHexString(groupNum) + instanceHex, 16);

		// Start the Tor server socket
		Tor61Router router = new Tor61Router(serviceData);
		Thread t1 = new Thread(router);
		t1.start();

		// Upon creation, register this Tor61 Node
		RegistrationAgent agent = null;
		try {
			agent = new RegistrationAgent(InetAddress.getByName(regServerHost), regServerPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		agent.register(router.port, Long.toString(serviceData),
				"Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum));

		// Once finished creating circuit, start HTTP Proxy
		HttpProxy proxy = new HttpProxy(httpProxyPort, router);
		Thread t2 = new Thread(proxy);
		t2.start();

		// Create a circuit
		// Fetch all nodes that are ours
		List<Tor61NodeInfo> routerInfos = agent.fetch("Tor61Router-" + String.format("%04d", groupNum));
		
		// Remove our own node from the fetched list
		for (int i = 0; i < routerInfos.size(); i++) {
			Tor61NodeInfo item = routerInfos.get(i);
			if (item.serviceData.equals(Long.toString(serviceData))) {
				routerInfos.remove(i);
			}
		}
		if (routerInfos.size() < 1) {
			System.out.println("We form a circuit of length 3 with unique nodes.");
			System.out.println("Circuit not created.");
		} else {
			Random r = new Random();
			Tor61NodeInfo node = routerInfos.get(r.nextInt(routerInfos.size()));
			router.connect(node, Long.toString(serviceData));
		}
	}
}