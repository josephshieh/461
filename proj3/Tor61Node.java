import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
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
		//System.out.println("Service Data:" + serviceData);
		
		List<Tor61NodeInfo> routerInfos = agent.fetch("Tor61Router-" + String.format("%04d", groupNum));
		/*List<Tor61NodeInfo> routerInfos = new ArrayList<Tor61NodeInfo>();
		try {
			routerInfos.add(new Tor61NodeInfo(InetAddress.getByName("172.28.7.64"), 61297, "79953922"));
			routerInfos.add(new Tor61NodeInfo(InetAddress.getByName("172.28.7.64"), 61298, "79953923"));
			routerInfos.add(new Tor61NodeInfo(InetAddress.getByName("172.28.7.64"), 61299, "79953924"));
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/
		// Remove our own node from the fetched list
		for (int i = 0; i < routerInfos.size(); i++) {
			Tor61NodeInfo item = routerInfos.get(i);
			if (item.serviceData.equals(Long.toString(serviceData))) {
				routerInfos.remove(i);
			}
		}

		if (routerInfos.size() < 3) {
			System.out.println("We form a circuit of length 3 with unique nodes.");
			System.out.println("Circuit not created.");
		} else {
			Random r = new Random();
			// Create first hop
			int hop1 = r.nextInt(routerInfos.size());
			Tor61NodeInfo node1 = routerInfos.get(hop1);
			System.out.println("hop1 dest:" + node1.serviceData);
			router.connect(node1, Long.toString(serviceData), null);

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Create second hop
			routerInfos.remove(hop1); // so we can't connect to same node twice
			int hop2 = r.nextInt(routerInfos.size());
			Tor61NodeInfo node2 = routerInfos.get(hop2);
			System.out.println("hop2 dest:" + node2.serviceData);
			router.relayExtend(node2);

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Create third hop
			routerInfos.remove(hop2);
			int hop3 = r.nextInt(routerInfos.size());
			Tor61NodeInfo node3 = routerInfos.get(hop3);
			System.out.println("hop3 dest:" + node3.serviceData);
			router.relayExtend(node3);

			System.out.println("Proxy is ready to accept client connections");
		}
	}
}