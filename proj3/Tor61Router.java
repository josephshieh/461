import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class Tor61Router implements Runnable {
	public static final int TOR_CELL_LENGTH = 512;

	int port;
	long serviceData;
	RoutingTable routingTable;
	Map<Tor61NodeInfo, Socket> torSockets;
	Map<Long, Set<Integer>> aidToCids;
	Map<Socket, Long> socketToAid; // for getting agent id of sender => look up in routing table
	Map<Long, Socket> aidToSocket; // for forwarding

	public Tor61Router(long serviceData) {
		this.serviceData = serviceData;
		this.routingTable = new RoutingTable();
		this.torSockets = new HashMap<Tor61NodeInfo, Socket>();
		this.aidToCids = new HashMap<Long, Set<Integer>>();
		this.socketToAid = new HashMap<Socket, Long>();
		this.aidToSocket = new HashMap<Long, Socket>();
	}

	public void connect(Tor61NodeInfo node, String serviceData) {
		Socket send = null;
		if (!torSockets.containsKey(node)) {
			// Open a new TCP connection
			try {
				send = new Socket(node.address.toString().substring(1), node.port);
				send.setKeepAlive(true);
				torSockets.put(node, send);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			open(send, node, serviceData);
		} else {
			send = torSockets.get(node);
			try {
				send.setKeepAlive(true);
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		//create?

	}

	public void open(Socket send, Tor61NodeInfo node, String serviceData) {
		// Can only assign odd numbers to circuit id
		// Send "open" message
		byte[] m = new byte[TOR_CELL_LENGTH];
		m[0] = (byte) 0;
		m[1] = (byte) 0;
		m[2] = (byte) 0x05;
		byte[] opener = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(Long.parseLong(serviceData)).array();
		m[3] = opener[4]; // Service data
		m[4] = opener[5];
		m[5] = opener[6];
		m[6] = opener[7];
		byte[] opened = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(Long.parseLong(node.serviceData)).array();
		m[7] = opened[4]; // Service data
		m[8] = opened[5];
		m[9] = opened[6];
		m[10] = opened[7];
		Arrays.fill(m, 11, 512, (byte) 0); // pad rest of array with zeros
		OutputStream outputStream = null;
		InputStream inputStream = null;
		DataInputStream dis = null;
		try {
			inputStream = send.getInputStream();
			outputStream = send.getOutputStream();
			// Write to web server as string
			outputStream.write(m);
			outputStream.flush();
			// Read response from the "opened"
			byte[] buffer = new byte[TOR_CELL_LENGTH];
			dis = new DataInputStream(inputStream);
			while (dis.available() > 0) { // if anything is available, its guaranteed to be 512 bytes
				dis.readFully(buffer);
				// Received an "opened" message
				byte[] circIdBytes = new byte[2];
				circIdBytes[0] = buffer[0];
				circIdBytes[1] = buffer[1];
				short circId = ByteBuffer.wrap(circIdBytes).getShort();
				int type = buffer[2];
				RouterCircuit dest = null;
				if (type == 6) { // Received "opened" message
					System.out.println("Received message: OPENED");
					aidToSocket.put(Long.parseLong(node.serviceData), send);
					dest = create(send, serviceData);
				} else if (type == 7) {
					System.out.println("Open failed");
				} else if (type == 2) {
					System.out.println("Received message: CREATED");
					routingTable.addRoute(new RouterCircuit(-1, -1), dest);
				} else{
					System.out.println("Incorrect message received.");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (inputStream != null) {
					inputStream.close();
				}
				if (outputStream != null) {
					outputStream.close();
				}
				if (dis != null) {
					dis.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public RouterCircuit create(Socket send, String serviceData) {
		// Send "create" message
		long agentId = Long.parseLong(serviceData);
		Random r = new Random();
		int circId;
		while (true) {
			circId = r.nextInt(65536);
			if (circId == 0 || circId % 2 == 0) {
				circId ++;
			}
			Set<Integer> cids;
			if (!aidToCids.containsKey(agentId)) {
				cids = new HashSet<Integer>();
				cids.add(circId);
				aidToCids.put(agentId, cids);
			} else {
				cids = aidToCids.get(agentId);
				if (cids.contains(circId)) {
					continue;
				} else {
					cids.add(circId);
					aidToCids.put(agentId, cids);
				}
			}
			break;
		}
		byte[] m = new byte[TOR_CELL_LENGTH];
		byte[] circIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(circId).array();
		m[0] = circIdBytes[2]; // Service data
		m[1] = circIdBytes[3];
		m[2] = (byte) 0x01;
		try {
			OutputStream outputStream = send.getOutputStream();
			outputStream.write(m);
			outputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new RouterCircuit(agentId, circId);
	}

	public void relayBegin(int streamId, String destAddr) {
		String[] destAddrString = destAddr.split(":");
		int port = Integer.parseInt(destAddrString[0]);
		String destIpAddr = destAddrString[1];
		byte[] m = new byte[TOR_CELL_LENGTH];
		RouterCircuit dest = routingTable.getDest(new RouterCircuit(-1, -1)); // (-1, -1) is starting point
		int circId = -1;
		long agentId = -1;
		if (dest != null) {
			circId = dest.circuitId;
			agentId = dest.agentId;
		}
		byte[] circIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(circId).array();
		m[0] = circIdBytes[2]; // circuit id
		m[1] = circIdBytes[3];
		m[2] = (byte) 0x03; // RELAY
		byte[] sidBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(streamId).array();
		m[3] = sidBytes[2]; // stream id
		m[4] = sidBytes[3];
		for (int i = 5; i < 11; i ++) { // zeroing out zero field and digest field
			m[i] = 0;
		}
		char[] destAddrChars = destAddr.toCharArray();
		int bodyLength = destAddrChars.length + 1; // plus null terminator
		byte[] bodyLengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(bodyLength).array();
		m[11] = bodyLengthBytes[2]; // body length
		m[12] = bodyLengthBytes[3];
		m[13] = (byte) 0x01; // BEGIN
		for (int i = 0; i < bodyLength - 1; i ++) {
			m[i + 14] = (byte) destAddrChars[i];
		}
		m[14 + bodyLength] = '\0';
		Arrays.fill(m, 14 + bodyLength + 1, 512, (byte) 0);
		try {
			// Send the relay begin cell
			Tor61NodeInfo destNode = new Tor61NodeInfo(InetAddress.getByName(destIpAddr), port, agentId + "");
			Socket send = torSockets.get(destNode);
			OutputStream outputStream = send.getOutputStream();
			outputStream.write(m);
			outputStream.flush();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(0);
			port = server.getLocalPort();
			System.out.println("Tor61 router is up on port: " + port);
		} catch (IOException e) {
			System.out.println("Failed to create server socket");
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
		}
	}

	/**
	 * This is for listening to a specific client's request, and create a new thread to redirect the request
	 * to the web server.
	 */
	class Listen implements Runnable {
		Socket src;
		public static final String EOF = "\r\n"; // CR LF String
		public Listen(Socket client) {
			this.src = client;
		}

		@Override
		public void run() {
			try{
				OutputStream outputStream = src.getOutputStream();
				InputStream inputStream = src.getInputStream();
				byte[] buffer = new byte[TOR_CELL_LENGTH];
				int len;
				// Reading input
				while((len = inputStream.read(buffer)) > 0) { // should always read in 512 bytes
					System.out.println("read");
					int circId = 0;
					// convert the lifetime bytes to a value
					for (int i = 0; i < 2; i++) {
						circId = (circId << 8) + (buffer[i] & 0xff);
					}
					System.out.println("Circuit id: " + circId);
					int type = buffer[2];
					byte[] m = new byte[TOR_CELL_LENGTH];
					if (type == 5) { // Open
						System.out.println("Received message: OPEN");
						long opener = 0;
						for (int i = 3; i < 7; i++) {
							opener = (opener << 8) + (buffer[i] & 0xff);
						}
						long opened = 0;
						for (int i = 7; i < 11; i++) {
							opened = (opened << 8) + (buffer[i] & 0xff);
						}
						System.out.println(opener + ", " + opened);
						Tor61NodeInfo info = new Tor61NodeInfo(src.getInetAddress(), src.getLocalPort(), opener + "");
						if (serviceData == (int) opened && !torSockets.containsKey(info)) {
							torSockets.put(info, src);
							socketToAid.put(src, opener);
						} else {
							// Received this message even though wrong address

						}
						// Reply with "opened" message
						m[0] = (byte) 0;
						m[1] = (byte) 0;
						m[2] = (byte) 0x06;
						for (int i = 3; i < TOR_CELL_LENGTH; i ++) { // This includes agent id of opener and opened, and padding
							m[i] = buffer[i];
						}
						socketToAid.put(src, opener);
					} else if (type == 1) { // Create
						// Reply with "CREATED"
						m[0] = buffer[0];
						m[1] = buffer[1];
						m[2] = (byte) 0x02;
						for (int i = 3; i < TOR_CELL_LENGTH; i ++) {
							m[i] = buffer[i];
						}
					} else if (type == 4) { // Destroy

					} else if (type == 3) { // Relay
						long agentId = -1;
						if (socketToAid.containsKey(src)) {
							agentId = socketToAid.get(src);
						}
						int streamId = 0;
						// convert the lifetime bytes to a value
						for (int i = 3; i < 5; i++) {
							streamId = (streamId << 8) + (buffer[i] & 0xff);
						}
						RouterCircuit dest = routingTable.getDest(new RouterCircuit(agentId, circId));
						if (dest != null) {
							Socket forward = aidToSocket.get(dest.agentId);
							int destCircId = dest.circuitId;
							byte[] destCircIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
									.putInt(destCircId).array();
							buffer[0] = destCircIdBytes[2]; // circuit id
							buffer[1] = destCircIdBytes[3];
							OutputStream forwardOutputStream = forward.getOutputStream();
							forwardOutputStream.write(buffer);
							forwardOutputStream.flush();
						} else { // at the endpoint
							int bodyLength = 0;
							for (int i = 11; i < 13; i++) {
								bodyLength = (bodyLength << 8) + (buffer[i] & 0xff);
							}
							int relayCmd = buffer[13];
							if (relayCmd == 1) { // BEGIN
								String serverAddr = "";
								for (int i = 0; i < bodyLength - 1; i ++) { // Don't read null terminator
									serverAddr += (char) buffer[14 + i];
								}
								System.out.println(serverAddr);
								String[] serverAddrString = serverAddr.split(":");

							} else if (relayCmd == 2) {

							} else if (relayCmd == 3) {

							} else if (relayCmd == 6) {

							} else {
								System.out.println("Error: Listener class received response.");
							}
						}
					}
					outputStream.write(m);
					outputStream.flush();
				}
			} catch (IOException e) {
				System.out.println("Failed to get socket.");
				System.exit(1);
			}
		}
	}
}
