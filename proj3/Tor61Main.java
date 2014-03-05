import java.net.InetAddress;
import java.net.UnknownHostException;


public class Tor61Main {
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

	public static void usage() {
		System.out.println("Usage: java Tor61Node <reg server host/ip> <reg server port> <group number> <instance number> <HTTP Proxy port>");
		System.exit(1);
	}
}
