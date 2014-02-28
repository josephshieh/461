import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

/**
 * Joseph Shieh, 1031718, josephs2@uw.edu
 * Sergey Naumets, 1025573, snaumets@uw.edu
 * CSE 461 Networks Project 3, .
 */

public class Tor61Node {
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

		ServerSocket torServerSocket = null;
		try {
			torServerSocket = new ServerSocket();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		RegistrationAgent agent = null;
		try {
			agent = new RegistrationAgent(InetAddress.getByName(regServerHost), regServerPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		String instance = String.format("%04d", instanceNum);
		int serviceData = Integer.parseInt(Integer.toHexString(groupNum) + instance, 16);
		agent.register(torServerSocket.getLocalPort(), Integer.toString(serviceData),
				"Tor61Router-" + String.format("%04d", groupNum) + "-" + instance);
		agent.fetch("");

		/*
		ServerSocket server = null;
		try {
			server = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("Failed to create server socket on port: " + port);
			System.exit(-1);
		}

		while (true) { // Listen forever, terminated by Ctrl-C
			try {
				Socket client = server.accept();
				// Now that we have a client to to communicate with, create new thread
				Listen l = new Listen(client);
				Thread t = new Thread(l);
				t.start();
			} catch (IOException e) {
				System.out.println("Failed to accept connection.");
			}
		}*/

	}

	public static void usage() {
		System.out.println("Usage: java Tor61Node <reg server host/ip> <reg server port> <group number> <instance number> <HTTP Proxy port>");
		System.exit(1);
	}

}