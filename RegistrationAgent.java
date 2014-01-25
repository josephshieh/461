/* Name: Sergey Naumets, Email: snaumets@uw.edu, SID#: 1025573
 * Name: Joseph Shieh, Email: josephs2@uw.edu, SID#: 1031718
 * CSE461 Project1, Winter 2014
 * 
 * This class will act as a client to a registration service, which will be a discovery mechanism that allows nodes, like
 * this class, to find each other. It is really just an agent that supports another application, that will also be called
 * a service. It will be able to send certain requests and receive responses, which will allow it to communicate with the
 * registration server.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RegistrationAgent {
	public static void main(String args[]) throws Exception {
		String hostname = null;
		int port = -1;
		if (args.length == 2) {
			hostname = args[0]; // the registration service host name
			try {
				port = Integer.parseInt(args[1]); // the second argument will be a service port number
			} catch (NumberFormatException e) {
				// the given argument wasnt an integer, so we exit
				System.out.println("The given port was not an integer.");
				System.out.println("Usage: java RegistrationAgent <registration service host name> <service port>");
				System.exit(1);
			}
		} else {
			System.out.println("Wrong number of arguments given.");
			System.out.println("Usage: java RegistrationAgent <registration service host name> <service port>");
			System.exit(1);
		}

		Send s = new Send(InetAddress.getByName(hostname), port);
		Thread t1 = new Thread(s);
		t1.start();

	}
}

class Send implements Runnable {
	private int sequenceNum;
	private final InetAddress destAddr;
	private final int destPort;

	public Send(InetAddress destAddr, int port) {
		sequenceNum = 0;
		this.destAddr = destAddr;
		this.destPort = port;
	}

	public void usageAll() {
		System.out.println("The issued command is not recognized.");
		System.out.println("Command options: ");
		usage("r");
		usage("u");
		usage("f");
		usage("p");
		System.out.println("	q ==> Quit execution");

	}

	public void usage(String cmd) {
		if (cmd.equals("r")) {
			System.out.println("	r portnum data serviceName ==> Send Register message to the server.");
		} else if (cmd.equals("u")) {
			System.out.println("	u portnum ==> Send Unregister message to registration service.");
		} else if (cmd.equals("f")) {
			System.out.println("	f <name prefix> ==> Send a Fetch message to registration service");
		} else if (cmd.equals("p")) {
			System.out.println("	p ==> Send Probe to registration service");
		}
	}

	@Override
	public void run() {
		// Here, we will accept commands from stdin that cause it to send messages to the registration service
		// identified by the command line arguments, to read its responses the service sends, and to print
		// appropriate messages about the result of the interaction.
		String prompt = "Enter r(egister), u(nregister), f(etch), p(robe), or q(uit):";
		System.out.println(prompt);
		// This reader will read a line of input at a time from stdin and send it to the client
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String command = "";
		DatagramSocket socket = null;
		try {
			while(!(command = input.readLine()).equals("q")) { // quit if the user types "q"
				// Determine which command it is and execute accordingly

				// Command: r portnum data serviceName
				// This will send a Register message to the server, using the IP of the running machine and the
				// port, data, and service name given with the command. We will print an indication of whether
				// or not the register was successful.
				// Assumption: no whitespace in arguments. Whitespace will only be used for delimiting between arguments.

				// Command: u portnum
				// Send an Unregister message for your host's IP and the specified port.
				// We will print an indication of whether or not the unregister was successful.

				// Command: f <name prefix>
				// Send a Fetch message to the registration service. If successful, print what it returns.
				// Otherwise, print an indication that the Fetch failed.

				// Command: p
				// Send a Probe to the registration service and display an indication of whether or not it succeeded.

				if (command.startsWith("r")) {
					String[] args = command.split(" ");
					if (args.length != 4) {
						usage("r");
						continue;
					}
					int portnum = Integer.parseInt(args[1]);
					if (socket == null) {
						socket = new DatagramSocket(portnum);
					}
					String serviceData = args[2];
					String serviceName = args[3];
					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x01; // Register
					String ip;
					byte[] ipBytes;
					try {
						ip = InetAddress.getLocalHost().getHostAddress();
						ipBytes = ip.getBytes();
						m[4] = ipBytes[0]; // Service ip
						m[5] = ipBytes[1];
						m[6] = ipBytes[2];
						m[7] = ipBytes[3];
					} catch (UnknownHostException e) {
						e.printStackTrace();
						System.exit(1);
					}
					m[8] = (byte) (portnum & 0xFF); // Service port number
					m[9] = (byte) ((portnum >> 8) & 0xFF);
					byte[] dataBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
							.putLong(Long.parseLong(serviceData)).array();
					m[10] = dataBytes[4]; // Service data
					m[11] = dataBytes[5];
					m[12] = dataBytes[6];
					m[13] = dataBytes[7];
					int len = serviceName.getBytes().length;
					m[14] = (byte) len;
					byte[] serviceNameBytes = serviceName.getBytes();
					for (int i = 0; i < len; i ++) {
						m[15 + i] = serviceNameBytes[i];
					}
					DatagramPacket packet = new DatagramPacket(m, m.length, this.destAddr, this.destPort);
					try {
						socket.send(packet);
						this.sequenceNum ++;
					} catch (IOException e) {
						e.printStackTrace();
					}

					// Start listening to shit

				} else if (command.startsWith("u")) {

				} else if (command.startsWith("f")) {

				} else if (command.startsWith("p")) {

				} else {
					usageAll();
				}
				System.out.println(prompt);
			}
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}


class Listen implements Runnable {
	@Override
	public void run() {

	}
}