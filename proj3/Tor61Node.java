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

		String instance = String.format("%04d", instanceNum);
		int serviceData = Integer.parseInt(Integer.toHexString(groupNum) + instance, 16);

		// Start the HTTP Proxy
		HttpProxy proxy = new HttpProxy(httpProxyPort);
		Thread t1 = new Thread(proxy);
		t1.start();

		// Start the Tor server socket
		Tor61Router router = new Tor61Router(serviceData);
		Thread t2 = new Thread(router);
		t2.start();

		// Upon creation, register this Tor61 Node
		RegistrationAgent agent = null;
		try {
			agent = new RegistrationAgent(InetAddress.getByName(regServerHost), regServerPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		agent.register(router.port, Integer.toString(serviceData),
				"Tor61Router-" + String.format("%04d", groupNum) + "-" + instance);

		List<Tor61NodeInfo> routerInfos = agent.fetch("Tor61Router-1220");
		// Create a circuit
		Random r = new Random();
		Tor61NodeInfo node = routerInfos.get(r.nextInt(routerInfos.size()));
		router.connect(node, Integer.toString(serviceData));
	}

	public static void usage() {
		System.out.println("Usage: java Tor61Node <reg server host/ip> <reg server port> <group number> <instance number> <HTTP Proxy port>");
		System.exit(1);
	}

	public static void main(String[] args)  {
		if (args.length != 5) {
			System.out.println("Wrong number of arguments given.");
			usage();
		}
		try {
			System.out.println("thisHostIp = " + InetAddress.getLocalHost().getHostAddress().toString());
		} catch (UnknownHostException e2) {
			e2.printStackTrace();
		}
		// Get the <reg server host/ip> arg
		String regServerHost = null;
		try {
			regServerHost = InetAddress.getByName(args[0]).getHostAddress().toString();
		} catch (UnknownHostException e1) {
			// not able to get the name of the given host/ip, so we exit
			System.out.println("The given registration server host/ip doesnt seem to be valid.");
			usage();
		}

		// Get the <reg server port> arg
		int regServerPort = -1;
		try {
			regServerPort = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			// the given argument wasnt an integer, so we exit
			System.out.println("The given registration server port was not an integer.");
			usage();
		}

		// Get the <group number> arg
		int groupNum = -1;
		try {
			groupNum = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			// the given argument wasnt an integer, so we exit
			System.out.println("The given group number was not an integer.");
			usage();
		}
		// Get the <instanceNum number> arg
		int instanceNum = -1;
		try {
			instanceNum = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			// the given argument wasnt an integer, so we exit
			System.out.println("The given instance number was not an integer.");
			usage();
		}

		// Get the <httpProxyPort number> arg
		int httpProxyPort = -1;
		try {
			httpProxyPort = Integer.parseInt(args[4]);
		} catch (NumberFormatException e) {
			// the given argument wasnt an integer, so we exit
			System.out.println("The given HTTP Proxy Port was not an integer.");
			usage();
		}

		Tor61Node node = new Tor61Node(regServerHost, regServerPort, groupNum, instanceNum, httpProxyPort);

	}


}