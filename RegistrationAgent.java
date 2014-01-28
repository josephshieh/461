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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

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
		// Print out ip addresses of registration server and the host machine
		System.out.println("regServerIP = " + InetAddress.getByName(hostname).getHostAddress().toString());
		System.out.println("thisHostIP = " + InetAddress.getLocalHost().getHostAddress().toString());
		Send s = new Send(InetAddress.getByName(hostname), port);
		Thread t1 = new Thread(s);
		t1.start();

	}
}

/** This class will be capable of sending requests to a given address and port, taking in keyboard input from the user
 *  to determine what type of messages to send to the regsitration server. It will print out feedback accordingly.
 * */
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
	/***
	 * This is for threads to reregister periodically
	 */
	class Reregister implements Runnable {
		// Given a lifetime of a registration, we will periodically reregister by dividing by this factor
		private final int fraction = 2;
		int seconds;
		int portnum;
		String serviceData;
		String serviceName;
		DatagramSocket socket;

		public Reregister(int seconds, int portnum, String serviceData, String serviceName, DatagramSocket socket) {
			this.seconds = seconds / fraction;
			this.portnum = portnum;
			this.serviceData = serviceData;
			this.serviceName = serviceName;
			this.socket = socket;
		}

		@Override
		public void run() {
			while (true) {
				try {
					// Wait for seconds long to try to reregister
					Thread.sleep(seconds * 1000);
				} catch (InterruptedException e) {
					// Once interrupted, we will exit the loop which ends the thread
					break;
				}
				DatagramPacket packet = registerService(portnum, serviceData, serviceName);
				seconds = tryRegister(socket, packet, 3, false) / fraction;
			}
		}
	}
	// Get life time of a registration
	private long getLifetime(DatagramSocket socket, int expectedSeqNum, boolean printAll) {
		long lifetime = 0;
		byte[] recvData = new byte[1024];
		DatagramPacket packet = new DatagramPacket(recvData, recvData.length);
		// will wait forever until a packet is received, or until timeout occurs
		try {
			socket.receive(packet);
		} catch (SocketTimeoutException e) {
			return lifetime;
		} catch (IOException e) {
			e.printStackTrace();
		}

		// when we get here, we have received a packet with some data
		// print the data contained in the arriving packet
		String data = new String(packet.getData(), 0, packet.getLength());
		byte[] dataBytes = data.getBytes();
		if (dataBytes[0] != (byte) 0xC4 && dataBytes[1] != (byte) 0x61) {
			System.out.println("Unknown message: Magic Number mismatch.");
		} else {
			int seqNum = dataBytes[2];
			if (expectedSeqNum != seqNum) { // Do nothing if
				return lifetime;
			}
			if (dataBytes[3] == (byte) 0x02) { // Registered message
				// convert the lifetime bytes to a value
				for (int i = 4; i <= 5; i++) {
					lifetime = (lifetime << 8) + (dataBytes[i] & 0xff);
				}
				try {
					if (printAll) {
						System.out.println("Register " + InetAddress.getLocalHost().getHostAddress() + ":"
								+ socket.getLocalPort() + " successful: lifetime = " + lifetime);
					}
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Incorrect type of message received.");
			}
		}
		return lifetime;
	}

	// This will try to register a given port to the registration server, will return 0 if failed, positive integer
	// representing the life time of the registration otherwise
	private int tryRegister(DatagramSocket socket, DatagramPacket packet, int numTries, boolean printAll) {
		int lifetime = 0;
		try {
			int sendTry = 0;
			while (sendTry < numTries) {
				socket.send(packet);
				// Listen for server response; expecting an Registered message with lifetime of connection
				// Wait for response, verify sequence number
				lifetime = (int) getLifetime(socket, sequenceNum, printAll);
				sendTry++;
				if (lifetime > 0) {
					return lifetime;
				} else {
					System.out.println("Timed out waiting for reply to REGISTER message.");
				}
			}
			if (sendTry == numTries) {
				System.out.println("REGISTER FAILED: Sent " + sendTry + " REGISTER messages but got no reply.");
			} else {
				if (this.sequenceNum == 255) {
					this.sequenceNum = 0;
				} else {
					this.sequenceNum ++;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lifetime;
	}
	// This method will wait for a response along the given socket (DatagramSocket), verify that the message data
	// starts with the magic number 0xC461 and that the message's enclosed sequence number matches the given
	// expectedSeqNum (int). Upon success, it will print a corresponding message and return true so that the caller
	// knows whether to send the message again or not.
	private boolean waitForResponse(DatagramSocket socket, int expectedSeqNum) {
		byte[] recvData = new byte[1024];
		DatagramPacket packet = new DatagramPacket(recvData, recvData.length);
		// will wait forever until a packet is received, or until timeout occurs
		try {
			socket.receive(packet);
		} catch (SocketTimeoutException e) {
			return false;
		} catch (IOException e) {
			e.printStackTrace();
		}
		// when we get here, we have received a packet with some data
		// print the data contained in the arriving packet
		String data = new String(packet.getData(), 0, packet.getLength());
		byte[] dataBytes = data.getBytes();
		if (dataBytes[0] != (byte) 0xC4 && dataBytes[1] != (byte) 0x61) {
			System.out.println("Unknown message: Magic Number mismatch.");
		} else {
			int seqNum = dataBytes[2];
			if (expectedSeqNum != seqNum) { // Do nothing if
				return false;
			}
			if (dataBytes[3] == (byte) 0x02) { // Registered message
				long lifetime = 0;
				// convert the lifetime bytes to a value
				for (int i = 4; i <= 5; i++) {
					lifetime = (lifetime << 8) + (dataBytes[i] & 0xff);
				}
				try {
					System.out.println("Register " + InetAddress.getLocalHost().getHostAddress() + ":"
							+ socket.getLocalPort() + " successful: lifetime = " + lifetime);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			} else if (dataBytes[3] == (byte) 0x04) { // FetchResponse
				int numEntries = dataBytes[4];
				for (int i = 0; i < numEntries; i++) {
					long[] ipNums = new long[4];
					for (int j = 0; j < 4; j++) {
						ipNums[j] = (dataBytes[5 + i * 10 + j] & 0xff);
					}
					long portnum = 0;
					for (int j = 4; j < 6; j++) {
						portnum = (portnum << 8) + (dataBytes[5 + i * 10 + j] & 0xff);
					}
					long serviceData = 0;
					for (int j = 6; j < 10; j++) {
						serviceData = (serviceData << 8) + (dataBytes[5 + i * 10 + j] & 0xff);
					}
					System.out.println("[" + (i + 1) + "]  " + ipNums[0] + "." + ipNums[1] + "." + ipNums[2] + "." +ipNums[3] +
							"  " + portnum + "  " + serviceData + " (0x" + Long.toHexString(serviceData) + ")");

				}
			} else if (dataBytes[3] == (byte) 0x07) { // ACK
				System.out.println("Success");
			}
		}
		return true;
	}

	// This method will attempt to send the given packet (DatagramPacket) from given socket (DatagramSocket) numTries (Int) times
	// and will print custom error messages specific to the type of message being sent, specified by msgType (String). It
	// will also increment the global message sequence number upon success.
	private void trySend(DatagramSocket socket, DatagramPacket packet, int numTries, String msgType, boolean printAll) {
		try {
			int sendTry = 0;
			while (sendTry < numTries) {
				socket.send(packet);
				// Listen for server response; expecting an Registered message with lifetime of connection
				// Wait for response, verify sequence number
				boolean success = waitForResponse(socket, sequenceNum);
				sendTry++;
				if (success) {
					break;
				} else {
					if (printAll) {
						System.out.println("Timed out waiting for reply to " + msgType + " message.");
					}
				}
			}
			if (sendTry == numTries) {
				System.out.println("Sent " + sendTry + " " + msgType + " messages but got no reply.");
				System.out.println(msgType + " failed.");
			} else {
				if (this.sequenceNum == 255) {
					this.sequenceNum = 0;
				} else {
					this.sequenceNum ++;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// This will build up a datagram packet for a register request to send to the registration server
	private DatagramPacket registerService(int portnum, String serviceData, String serviceName) {
		int msgLength = 0;
		byte[] m = new byte[1000];
		m[0] = (byte) 0xC4;
		m[1] = (byte) 0x61;
		m[2] = (byte) sequenceNum;
		m[3] = (byte) 0x01; // Register
		msgLength += 4;
		String ip;
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
			String[] nums = ip.split("\\.");
			int[] intNums = new int[4];
			for (int i = 0; i < 4; i++) {
				int j = Integer.parseInt(nums[i]);
				intNums[i] = j;
			}
			m[4] = (byte) intNums[0]; // Service ip
			m[5] = (byte) intNums[1];
			m[6] = (byte) intNums[2];
			m[7] = (byte) intNums[3];
			msgLength += 4;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		m[8] = (byte) ((portnum >> 8) & 0xFF); // Service port number, will be in big endian order this way
		m[9] = (byte) (portnum & 0xFF);
		msgLength += 2;
		byte[] dataBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(Long.parseLong(serviceData)).array();
		m[10] = dataBytes[4]; // Service data
		m[11] = dataBytes[5];
		m[12] = dataBytes[6];
		m[13] = dataBytes[7];
		msgLength += 4;
		int len = serviceName.getBytes().length;
		m[14] = (byte) len;
		msgLength += 1;
		byte[] serviceNameBytes = serviceName.getBytes();
		for (int i = 0; i < len; i ++) {
			m[15 + i] = serviceNameBytes[i];
		}
		msgLength += len;
		return new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
	}

	@Override
	public void run() {
		// Here, we will accept commands from stdin that cause it to send messages to the registration service
		// identified by the command line arguments, to read its responses the service sends, and to print
		// appropriate messages about the result of the interaction.
		DatagramSocket sendSocket = null;
		DatagramSocket listenSocket = null;
		// try to find two consecutive ports
		while (sendSocket == null && listenSocket == null) {
			try {
				sendSocket = new DatagramSocket(); // will find available port
			} catch (SocketException e) { // also catches BindException in case port is being used
				sendSocket = null;
				continue;
			}
			int portnum = sendSocket.getLocalPort();
			try {
				listenSocket = new DatagramSocket(portnum + 1);
			} catch (SocketException e) { // also catches BindException in case port is being used
				sendSocket.close();
				sendSocket = null;
				listenSocket = null;
			}
		}
		try {
			sendSocket.setSoTimeout(5000); // == 5 seconds
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		String prompt = "Enter r(egister), u(nregister), f(etch), p(robe), or q(uit):";
		System.out.println(prompt);
		// This reader will read a line of input at a time from stdin and send it to the client
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String command = "";
		Map<Integer, Thread> reregThreads = new HashMap<Integer, Thread>();
		try {
			Listen probeListen = null;
			// This map will allow us to link every service that is registered to a Thread
			// the periodically reregisters that service. This will allow use to kill end that thread
			// once that service is unregistered.
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

				if (command.startsWith("r")) { //----- register -----------------------------------------------------
					/////////////////////////////////////// TODO: send no more than max packet size
					String[] args = command.split(" ");
					if (args.length != 4) {
						usage("r");
						continue;
					}
					int portnum = Integer.parseInt(args[1]);
					String serviceData = args[2];
					String serviceName = args[3];
					// If there is a thread running on this port, it must be reregistering, so we want to stop it
					if (reregThreads.containsKey(portnum)){
						reregThreads.get(portnum).interrupt();
					}
					DatagramPacket packet = registerService(portnum, serviceData, serviceName);

					// Start periodic reregistration
					int lifetime = tryRegister(sendSocket, packet, 3, true);
					if (lifetime > 0) {
						Reregister rereg = new Reregister(lifetime, portnum, serviceData, serviceName, sendSocket);
						Thread reregisterThread = new Thread(rereg);
						reregisterThread.start();
						if (reregThreads.containsKey(portnum)) {
							reregThreads.get(portnum).interrupt();
						}
						reregThreads.put(portnum, reregisterThread);
					}

					// Create a thread that will listen server's probes
					if (probeListen == null) {
						probeListen = new Listen(listenSocket);
						Thread probeThread = new Thread(probeListen);
						probeThread.start();
					}

				} else if (command.startsWith("u")) { //----- unregister -----------------------------------------------------
					int msgLength = 0;
					String[] args = command.split(" ");
					if (args.length != 2) {
						usage("u");
						continue;
					}
					int portnum = Integer.parseInt(args[1]);

					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x05; // Unregister
					msgLength += 4;
					String ip;
					try {
						ip = InetAddress.getLocalHost().getHostAddress();
						String[] nums = ip.split("\\.");
						int[] intNums = new int[4];
						for (int i = 0; i < 4; i++) {
							int j = Integer.parseInt(nums[i]);
							intNums[i] = j;
						}
						m[4] = (byte) intNums[0]; // Service ip
						m[5] = (byte) intNums[1];
						m[6] = (byte) intNums[2];
						m[7] = (byte) intNums[3];
						msgLength += 4;
					} catch (UnknownHostException e) {
						e.printStackTrace();
						System.exit(1);
					}
					m[8] = (byte) ((portnum >> 8) & 0xFF); // Service port number, will be in big endian order this way
					m[9] = (byte) (portnum & 0xFF);
					msgLength += 2;
					DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
					trySend(sendSocket, packet, 3, "UNREGISTER", true);

					if (reregThreads.containsKey(portnum)) {
						reregThreads.get(portnum).interrupt();
						reregThreads.remove(portnum);
					}
				} else if (command.startsWith("f")) { //----- fetch -----------------------------------------------------
					int msgLength = 0;
					String[] args = command.split(" ");
					if (args.length != 1 && args.length != 2) { // <name prefix> optional parameter
						usage("f");
						continue;
					}
					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x03; // Fetch
					msgLength += 4;
					String namePrefix = "";
					int namePrefixLen = 0;
					byte[] namePrefixBytes;
					if (args.length == 2) {
						namePrefix = args[1];
						namePrefixBytes = namePrefix.getBytes();
						namePrefixLen = namePrefixBytes.length;
						for (int i = 0; i < namePrefixLen; i ++) {
							m[5 + i] = namePrefixBytes[i];
						}
					}
					m[4] = (byte) namePrefixLen;
					msgLength += 1;
					msgLength += namePrefixLen;
					DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
					if (sendSocket == null) {
						System.out.println("No socket. Please register first.");
						continue;
					}
					trySend(sendSocket, packet, 3, "FETCH", true);
				} else if (command.startsWith("p")) { //----- probe -----------------------------------------------------
					int msgLength = 0;
					String[] args = command.split(" ");
					if (args.length != 1) {
						usage("p");
						continue;
					}

					if (sendSocket == null) {
						System.out.println("No socket. Please register first.");
						continue;
					}
					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x06; // Probe
					msgLength += 4;
					DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
					trySend(sendSocket, packet, 3, "PROBE", true);
				} else {
					usageAll();
				}
				System.out.println(prompt);
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// Shuts down all threads
			for (int port: reregThreads.keySet()) {
				Thread cur = reregThreads.get(port);
				cur.interrupt();
			}
			// Close both sending and listening sockets
			if (sendSocket != null) {
				sendSocket.close();
			}
			if (listenSocket != null) {
				listenSocket.close();
			}
		}
	}
}

/**
 * This class will allow us to listen to requests from the registration server
 */
class Listen implements Runnable {
	private final DatagramSocket socket;

	public Listen(DatagramSocket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		byte[] recvData = new byte[1024];
		try {
			while (true) {
				DatagramPacket packet = new DatagramPacket(recvData, recvData.length);
				// will wait forever until a packet is received, or timeout occurs
				socket.receive(packet);
				// when we get here, we have received a packet with some data
				// print the data contained in the arriving packet
				String data = new String(packet.getData(), 0, packet.getLength());
				byte[] dataBytes = data.getBytes();
				if (dataBytes[0] != (byte) 0xC4 && dataBytes[1] != (byte) 0x61) {
					System.out.println("Unknown message: Magic Number mismatch.");
				} else {
					if (dataBytes[3] == (byte) 0x06) { // Probe
						int seqNum = dataBytes[2];
						int msgLength = 0;
						byte[] m = new byte[1000];
						m[0] = (byte) 0xC4;
						m[1] = (byte) 0x61;
						m[2] = (byte) seqNum;
						m[3] = (byte) 0x07; // ACK
						msgLength += 4;
						InetAddress destAddr = packet.getAddress();
						int destPort = packet.getPort();
						DatagramPacket ack = new DatagramPacket(m, msgLength, destAddr, destPort);
						try {
							socket.send(ack);
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("I've been probed!");
					}
				}
			}
		} catch (SocketException e) {
			//This exception is expected, and is how we will cleanly exit this thread
			//System.out.println("SocketException: " + e.getMessage());
			return;
		} catch (IOException e) {
			System.out.println("IOException: " + e.getMessage());
			return;
		}
	}
}

