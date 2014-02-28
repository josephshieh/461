/** Name: Sergey Naumets, Email: snaumets@uw.edu, SID#: 1025573
 * Name: Joseph Shieh, Email: josephs2@uw.edu, SID#: 1031718
 * CSE461 Project1, Winter 2014
 * 
 * This class will act as a client to a registration service, which will be a discovery mechanism that allows nodes, like
 * this class, to find each other. It is really just an agent that supports another application, that will also be called
 * a service. It will be able to send certain requests and receive responses, which will allow it to communicate with the
 * registration server.
 */

import java.io.IOException;
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
	Map<Integer, Thread> reregThreads;
	int sequenceNum;
	Listen probeListen;
	DatagramSocket sendSocket;
	DatagramSocket listenSocket;
	InetAddress destAddr;
	int destPort;

	public RegistrationAgent(InetAddress destAddr, int destPort) {
		reregThreads = new HashMap<Integer, Thread>();
		sequenceNum = 0;
		probeListen = null;
		sendSocket = null;
		listenSocket = null;
		this.destAddr = destAddr;
		this.destPort = destPort;

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
	}

	public void register(int portnum, String serviceData, String serviceName){
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
	}

	public void fetch(String prefix){
		int msgLength = 0;
		byte[] m = new byte[1000];
		m[0] = (byte) 0xC4;
		m[1] = (byte) 0x61;
		m[2] = (byte) sequenceNum;
		m[3] = (byte) 0x03; // Fetch
		msgLength += 4;
		String namePrefix = "";
		int namePrefixLen = 0;
		byte[] namePrefixBytes;
		if (!prefix.equals("")) {
			namePrefix = prefix;
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
			return;
		}
		trySend(sendSocket, packet, 3, "FETCH", true);
	}

	public void unregister(int portnum) {
		int msgLength = 0;
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
	}

	public void probe() {
		int msgLength = 0;
		if (sendSocket == null) {
			System.out.println("No socket. Please register first.");
			return;
		}
		byte[] m = new byte[1000];
		m[0] = (byte) 0xC4;
		m[1] = (byte) 0x61;
		m[2] = (byte) sequenceNum;
		m[3] = (byte) 0x06; // Probe
		msgLength += 4;
		DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
		trySend(sendSocket, packet, 3, "PROBE", true);
	}

	public void shutdown() {
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

		byte[] dataBytes = packet.getData();
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
					byte[] dataBytes = packet.getData();
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
		byte[] dataBytes = packet.getData();
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
}

