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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class Tor61Router implements Runnable {
	public static final int TOR_CELL_LENGTH = 512;

	int port;
	long serviceData;
	RoutingTable routingTable;
	Map<Tor61NodeInfo, Socket> torSockets;
	Map<Long, Set<Integer>> aidToCids;
	Map<Socket, Long> socketToAid; // for getting agent id of sender => look up in routing table, <Socket, srcAgentId>
	Map<Long, Socket> aidToSocket; // for forwarding, <destAgentId, Socket>
	Map<Integer, RouterCircuit> responseRoutingTable; // multiplexing stream ids to circuits, <streamId, <startAgentId, startCircuit>>
	Map<Integer, Socket> sidToServerSocket; // so we know which web server to deliver packets to once at endpoint of circuit
	Map<Integer, Socket> sidToClientSocket;
	Map<Socket, Integer> webServerSocketToSid;
	Map<Socket, BlockingQueue<Tor61Cell>> socketToQueue; // This will link an endpoint socket (to a browser or web server) to a queue
	/*
	 * 
	 */
	public Tor61Router(long serviceData) {
		this.serviceData = serviceData;
		this.routingTable = new RoutingTable();
		this.torSockets = new HashMap<Tor61NodeInfo, Socket>();
		this.aidToCids = new HashMap<Long, Set<Integer>>();
		this.socketToAid = new HashMap<Socket, Long>();
		this.aidToSocket = new HashMap<Long, Socket>();
		this.responseRoutingTable = new HashMap<Integer, RouterCircuit>();
		this.sidToServerSocket = new HashMap<Integer, Socket>();
		this.sidToClientSocket = new HashMap<Integer, Socket>();
		this.webServerSocketToSid = new HashMap<Socket, Integer>();
		this.socketToQueue = new HashMap<Socket, BlockingQueue<Tor61Cell>>();
	}

	public class Connector implements Runnable {
		Tor61NodeInfo destNode;
		String openerAid;
		RouterCircuit src;

		public Connector(Tor61NodeInfo destNode, String openerAid, RouterCircuit src) {
			this.destNode = destNode;
			this.openerAid = openerAid;
			this.src = src;
		}

		@Override
		public void run() {
			Socket send = null;
			if (!torSockets.containsKey(destNode)) {
				// Open a new TCP connection
				try {
					send = new Socket(destNode.address.toString().substring(1), destNode.port);
					send.setKeepAlive(true);
					torSockets.put(destNode, send);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				open(send, destNode, openerAid, src);
			} else {
				send = torSockets.get(destNode);
				try {
					send.setKeepAlive(true);
				} catch (SocketException e) {
					e.printStackTrace();
				}
				create(send, this.openerAid);
				if (src != null) {
					// If there is a source, that means this is generated from a relay cell,
					// therefore we need to send back a relay extended cell
					relayExtended(src);
				}
			}
			// cant put create() here because open() blocks
		}

	}

	public void connect(Tor61NodeInfo destNode, String openerAid, RouterCircuit src) {
		Connector c = new Connector(destNode, openerAid, src);
		Thread t = new Thread(c);
		t.start();
	}

	/*
	 * 
	public void connect(Tor61NodeInfo destNode, String serviceData) {
		Socket send = null;
		if (!torSockets.containsKey(destNode)) {
			// Open a new TCP connection
			try {
				send = new Socket(destNode.address.toString().substring(1), destNode.port);
				send.setKeepAlive(true);
				torSockets.put(destNode, send);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			open(send, destNode, serviceData);
		} else {
			send = torSockets.get(destNode);
			try {
				send.setKeepAlive(true);
			} catch (SocketException e) {
				e.printStackTrace();
			}
			//create();
		}
	}
	 */

	/*
	 * src - optional -> not null means we are creating because of a relay extend
	 */
	public void open(Socket send, Tor61NodeInfo destNode, String openerAid, RouterCircuit src) {
		// Can only assign odd numbers to circuit id
		// Send "open" message
		byte[] m = new byte[TOR_CELL_LENGTH];
		m[0] = (byte) 0;
		m[1] = (byte) 0;
		m[2] = (byte) 0x05;
		byte[] opener = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(Long.parseLong(openerAid)).array();
		m[3] = opener[4]; // Service data
		m[4] = opener[5];
		m[5] = opener[6];
		m[6] = opener[7];
		byte[] opened = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(Long.parseLong(destNode.serviceData)).array();
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
			// Write to web server as bytes
			outputStream.write(m);
			outputStream.flush();
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: OPEN => {openerAid:" + openerAid + ", openedAid:" + destNode.serviceData + "}");
			// Read response from the "opened"
			byte[] buffer = new byte[TOR_CELL_LENGTH];
			dis = new DataInputStream(inputStream);
			RouterCircuit dest = null; // so it is not declared every iteration
			while (dis.available() >= 0) { // if anything is available, its guaranteed to be 512 bytes
				//try {
				dis.readFully(buffer);
				//} catch (EOFException e) { // When other node closes
				//System.out.println("Caught eof exception");
				//continue;
				//}
				// Received an "opened" message
				byte[] circIdBytes = new byte[2];
				circIdBytes[0] = buffer[0];
				circIdBytes[1] = buffer[1];
				int circId = 0;
				// convert the circuit id bytes to a value
				for (int i = 0; i < 2; i++) {
					circId = (circId << 8) + (circIdBytes[i] & 0xff);
				}
				int type = buffer[2];
				if (type == 6) { // Received "opened" message
					System.out.println(nodeName + " Received response: OPENED");
					socketToAid.put(send, Long.parseLong(destNode.serviceData));
					aidToSocket.put(Long.parseLong(destNode.serviceData), send);
					create(send, destNode.serviceData);
				} else if (type == 7) { // Open Failed
					System.out.println(nodeName + " Open failed");
				} else if (type == 2) { // Created
					System.out.println(nodeName + " Received response: CREATED");
					///////////////////////////////////////////////// extract other agent id and circuit id
					dest = new RouterCircuit(Long.parseLong(destNode.serviceData), circId);
					if (src != null) {
						// If there is a source, that means this is generated from a relay cell,
						// therefore we need to send back a relay extended cell
						routingTable.addRoute(src, dest);
						routingTable.addRoute(dest, src);
						relayExtended(src);
					} else { // This is a start point because we were not relay extending
						routingTable.addRoute(new RouterCircuit(-1, -1), dest);
						routingTable.addRoute(dest, new RouterCircuit(-1, -1));
					}

				} else if (type == 3) { // Relay
					int streamId = 0;
					// convert the stream id bytes to a value
					for (int i = 3; i < 5; i++) {
						streamId = (streamId << 8) + (buffer[i] & 0xff);
					}
					int bodyLength = 0;
					for (int i = 11; i < 13; i++) {
						bodyLength = (bodyLength << 8) + (buffer[i] & 0xff);
					}
					int relayCmd = buffer[13];
					if (relayCmd == 2) {
						System.out.println(nodeName + " Received: RELAY DATA");
					} else if (relayCmd == 3) {
						System.out.println(nodeName + " Received: RELAY END");
					} else if (relayCmd == 4) { // Connected
						System.out.println(nodeName + " Received: RELAY CONNECTED");
					} else if (relayCmd == 7) {
						System.out.println(nodeName + " Received: RELAY EXTENDED");
					} else if (relayCmd == 11) {
						System.out.println(nodeName + " Received: RELAY BEGIN FAILED");
					} else if (relayCmd == 12) {
						System.out.println(nodeName + " Received: RELAY EXTEND FAILED");
					} else {
						System.out.println(nodeName + " Error: Sender class received requests.");
					}
					if (relayCmd == 2 || relayCmd == 3 || relayCmd == 4 || relayCmd == 7 || relayCmd == 11 || relayCmd == 12) {
						// if valid relay cells
						// If were not start point, forward towards the start point
						RouterCircuit from = new RouterCircuit(socketToAid.get(send), circId);
						dest = routingTable.getDest(from);
						if (!dest.equals(new RouterCircuit(-1, -1))) { // forward this cell back
							relayForward(from, dest, buffer);
						} else if (relayCmd == 2) {
							Tor61Cell cell = new Tor61Cell(buffer);
							writeCellToQueueForSocket(sidToClientSocket.get(streamId), cell);
						} else if (relayCmd == 3) {
							Socket client = sidToClientSocket.get(streamId);
							if (client != null && !client.isClosed()) {
								client.close();
							}
							removeSidToClientSocket(streamId);
						}
					}

				} else {
					System.out.println(nodeName + " Incorrect message received. {circId: " + circId +", type: " + type + "}");
				}
			}
			System.out.println(nodeName + " Exit loop, shouldnt be here."); //////////////////////////////////////////////////////////
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

	/*
	 * 
	 */
	public void create(Socket send, String openerAid) {
		// Send "create" message
		long agentId = Long.parseLong(openerAid);
		Random r = new Random();
		int circId;
		while (true) {
			circId = r.nextInt(65536);
			if (circId == 0 || circId % 2 == 0) { // Nodes that start the TCP connection has to choose odd circ number
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
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: CREATE");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Forwards this relay cell on the circuit to the next node
	 * @param from - Where we are getting the data cell from
	 * @param to - Where we are sending the data cell to
	 * @param buffer - the relay cell
	 */
	public void relayForward(RouterCircuit from, RouterCircuit to, byte[] buffer) {
		System.out.println("Forwarding from...");
		System.out.println("Source: {agentId: " + from.agentId + ", circId: " + from.circuitId + "}");
		System.out.println("Dest: {agentId: " + to.agentId + ", circId: " + to.circuitId + "}");
		Socket forward = aidToSocket.get(to.agentId);
		int destCircId = to.circuitId;
		byte[] destCircIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(destCircId).array();
		buffer[0] = destCircIdBytes[2]; // circuit id
		buffer[1] = destCircIdBytes[3];
		OutputStream forwardOutputStream;
		try {
			forwardOutputStream = forward.getOutputStream();
			forwardOutputStream.write(buffer);
			forwardOutputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * 
	 */
	public void relayBegin(int streamId, String destAddr) {
		String[] destAddrString = destAddr.split(":");
		int port = Integer.parseInt(destAddrString[1]);
		String destIpAddr = destAddrString[0];

		byte[] m = new byte[TOR_CELL_LENGTH];
		RouterCircuit dest = routingTable.getDest(new RouterCircuit(-1, -1)); // (-1, -1) is starting point
		int circId = -1;
		long agentId = -1;
		if (dest != null) {
			circId = dest.circuitId;
			agentId = dest.agentId;
		}
		System.out.println("agentid:" + agentId);
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
		m[14 + bodyLength - 1] = '\0';
		Arrays.fill(m, 14 + bodyLength, 512, (byte) 0);
		try {
			// Send the relay begin cell
			Socket send = aidToSocket.get(dest.agentId); // get the socket that we need to write to
			//Socket send = torSockets.get(destNode);
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY BEGIN");
			OutputStream outputStream = send.getOutputStream(); //send.getOutputStream();
			outputStream.write(m);
			outputStream.flush();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void relayExtend(Tor61NodeInfo destNode) {
		if (destNode == null) {
			throw new IllegalArgumentException();
		}
		byte[] m = new byte[TOR_CELL_LENGTH];
		long agentId = Long.parseLong(destNode.serviceData);
		RouterCircuit dest = routingTable.getDest(new RouterCircuit(-1, -1));
		byte[] circIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(dest.circuitId).array();
		m[0] = circIdBytes[2]; // circuit id
		m[1] = circIdBytes[3];
		m[2] = (byte) 0x03; // RELAY
		m[3] = 0; // EXTEND - stream id = 0
		m[4] = 0;
		for (int i = 5; i < 11; i ++) { // zeroing out zero field and digest field
			m[i] = 0;
		}
		char[] destAddrChars = (destNode.address.toString().substring(1) + ":" + destNode.port).toCharArray();
		int destAddrLen = destAddrChars.length;
		int bodyLength = destAddrLen + 1 + 4; // plus null terminator + agentId
		byte[] bodyLengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(bodyLength).array();
		m[11] = bodyLengthBytes[2]; // body length
		m[12] = bodyLengthBytes[3];
		m[13] = (byte) 0x06; // EXTEND
		for (int i = 0; i < destAddrLen; i ++) {
			m[14 + i] = (byte) destAddrChars[i];
		}
		m[14 + destAddrLen] = '\0';
		byte[] agentIdBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
				.putLong(agentId).array();
		m[14 + destAddrLen + 1] = agentIdBytes[4];
		m[14 + destAddrLen + 2] = agentIdBytes[5];
		m[14 + destAddrLen + 3] = agentIdBytes[6];
		m[14 + destAddrLen + 4] = agentIdBytes[7];
		System.out.println("relay extend");
		System.out.println("ip: " + destNode.address.toString().substring(1));
		System.out.println("port: " + destNode.port);
		System.out.println("bodylength: " + bodyLength);
		System.out.println("agentId: " + agentId);
		Arrays.fill(m, 14 + bodyLength, 512, (byte) 0);
		try {
			// Send the relay extend cell
			Socket send = aidToSocket.get(dest.agentId); // get the socket that we need to write to
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY EXTEND to " + agentId);
			System.out.println("trying to send relay extend: socket isClosed " + (send.isClosed()));
			OutputStream outputStream = send.getOutputStream(); //send.getOutputStream();
			outputStream.write(m);
			outputStream.flush();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void relayExtended(RouterCircuit src) {
		byte[] m = new byte[TOR_CELL_LENGTH];
		byte[] circIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(src.circuitId).array();
		m[0] = circIdBytes[2]; // circuit id
		m[1] = circIdBytes[3];
		m[2] = (byte) 0x03; // RELAY
		m[3] = 0; // EXTENDED - stream id = 0
		m[4] = 0;
		for (int i = 5; i < 11; i ++) { // zeroing out zero field and digest field
			m[i] = 0;
		}
		m[11] = 0; // body length
		m[12] = 0;
		m[13] = (byte) 0x07; // EXTEND
		Arrays.fill(m, 14, 512, (byte) 0);
		try {
			System.out.println("srcNotNull:" + (src != null));
			System.out.println("aidToSocketKey:" + aidToSocket.containsKey(src.agentId));
			System.out.println("aidToSockNotNull:" + (aidToSocket.get(src.agentId) != null));
			OutputStream outputStream = aidToSocket.get(src.agentId).getOutputStream();
			outputStream.write(m);
			outputStream.flush();
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY EXTENDED");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void relayEnd(int streamId) {
		RouterCircuit start = responseRoutingTable.get(streamId);
		byte[] m = new byte[TOR_CELL_LENGTH];
		byte[] circIdBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(start.circuitId).array();
		m[0] = circIdBytes[2]; // circuit id
		m[1] = circIdBytes[3];
		m[2] = (byte) 0x03; // RELAY
		m[3] = 0; // END - stream id = 0
		m[4] = 0;
		for (int i = 5; i < 11; i ++) { // zeroing out zero field and digest field
			m[i] = 0;
		}
		m[11] = 0; // body length
		m[12] = 0;
		m[13] = (byte) 0x03; // END
		Arrays.fill(m, 14, 512, (byte) 0);
		try {
			OutputStream outputStream = aidToSocket.get(start.agentId).getOutputStream();
			outputStream.write(m);
			outputStream.flush();
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY END");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/*
	 * 
	 */
	public void relayDataCell(RouterCircuit from, int streamId, byte[] body, int bodyLen) {
		byte[] m = new byte[TOR_CELL_LENGTH];
		int circId = -1;
		long agentId = -1;
		RouterCircuit start = from.equals(new RouterCircuit(-1, -1)) ? routingTable.getDest(new RouterCircuit(-1,-1)): from;
		if (from != null) {
			circId = start.circuitId;
			agentId = start.agentId;
		}
		System.out.println("agentid:" + agentId);
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
		byte[] bodyLengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(bodyLen).array();
		m[11] = bodyLengthBytes[2]; // body length
		m[12] = bodyLengthBytes[3];
		m[13] = (byte) 0x02; // DATA
		for (int i = 0; i < bodyLen; i ++) {
			m[i + 14] = body[i];
		}
		int endBody = 14 + bodyLen;
		if ((endBody < TOR_CELL_LENGTH)) { // only pad bytes if any empty space is left after the body
			Arrays.fill(m, endBody, 512, (byte) 0);
		}
		try {
			// Send the relay data cell
			Socket send = aidToSocket.get(start.agentId); // get the socket that we need to write to
			//Socket send = torSockets.get(destNode);
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY DATA CELL");
			OutputStream outputStream = send.getOutputStream(); //send.getOutputStream();
			outputStream.write(m);
			outputStream.flush();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	public void connected(int streamId, int circId, Socket responseSocket) {
		byte[] m = new byte[TOR_CELL_LENGTH];
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
		int bodyLength = 0;
		byte[] bodyLengthBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
				.putInt(bodyLength).array();
		m[11] = bodyLengthBytes[2]; // 0x00
		m[12] = bodyLengthBytes[3]; // 0x00
		m[13] = (byte) 0x04; // CONNECTED
		Arrays.fill(m, 14, 512, (byte) 0); // zero out the rest of the body and message
		OutputStream outputStream = null;
		try {
			// Send the relay connected response cell
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			System.out.println(nodeName + " Sending message: RELAY CONNECTED");
			outputStream = responseSocket.getOutputStream();
			outputStream.write(m);
			outputStream.flush();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} /*finally { // closing this stream also closed the socket
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}*/
	}

	/*
	 * 
	 */
	public void addNewQueueForSocket(Socket socket) {
		socketToQueue.put(socket, new LinkedBlockingQueue<Tor61Cell>());
	}

	/*
	 * 
	 */
	public void writeCellToQueueForSocket(Socket socket, Tor61Cell cell) {
		try {
			socketToQueue.get(socket).put(cell);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/*
	 * 
	 */
	public Tor61Cell takeFromQueue(Socket socket) {
		Tor61Cell cell = null;
		try {
			cell = socketToQueue.get(socket).take(); // this call will block until queue isnt empty
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		return cell;
	}

	/*
	 * 
	 */
	@Override
	public void run() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(0); // will choose available port
			port = server.getLocalPort();
			System.out.println("Tor61 router is up on port: " + port);
		} catch (IOException e) {
			System.out.println("Failed to create server socket");
			System.exit(-1);
		}

		try {
			while (true) { // Listen forever, terminated by Ctrl-C
				try {
					Socket client = server.accept();
					client.setKeepAlive(true);
					//client.setSoTimeout(30000); // Set longer timeout for now
					// Now that we have a client to to communicate with, create new thread
					TorListener l = new TorListener(client, this.serviceData);
					Thread t = new Thread(l);
					t.start();
				} catch (IOException e) {
					System.out.println("Failed to accept connection.");
				}
			}
		} finally {
			if (server != null) {
				try {
					server.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public boolean sidToClientSocketContainsKey(int streamId) {
		return sidToClientSocket.containsKey(streamId);
	}
	public void addSidToClientSocket(int streamId, Socket client) {
		sidToClientSocket.put(streamId, client);
	}

	public void removeSidToClientSocket(int streamId) {
		sidToClientSocket.remove(streamId);
	}

	/**
	 * This is for listening to a specific client's request, and create a new thread to redirect the request
	 * to the web server.
	 */
	class TorListener implements Runnable {
		Socket src;
		long serviceData; // will allow us to print the name of the router who prints messages

		public TorListener(Socket client, long serviceData) {
			this.src = client;
			this.serviceData = serviceData;
		}

		/*
		 * 
		 */
		@Override
		public void run() {
			OutputStream outputStream = null;
			InputStream inputStream = null;
			DataInputStream dis = null;
			String serviceNameHex = Long.toString(this.serviceData, 16);
			int groupNum = Integer.valueOf(serviceNameHex.substring(0, serviceNameHex.length() - 4), 16);
			int instanceNum = Integer.valueOf(serviceNameHex.substring(serviceNameHex.length() - 4, serviceNameHex.length()), 16);
			String nodeName = "Tor61Router-" + String.format("%04d", groupNum) + "-" + String.format("%04d", instanceNum);
			try{
				outputStream = src.getOutputStream();
				inputStream = src.getInputStream();
				dis = new DataInputStream(inputStream);
				byte[] buffer = new byte[TOR_CELL_LENGTH];
				//int len;
				// Reading input
				while(dis.available() >= 0) { // if anything is available, its guaranteed to be 512 bytes
					dis.readFully(buffer); // EOFException when other node closes
					//System.out.println("read");
					int circId = 0;
					// convert the circuit id bytes to a value
					for (int i = 0; i < 2; i++) {
						circId = (circId << 8) + (buffer[i] & 0xff);
					}
					//System.out.println("Circuit id: " + circId);
					int type = buffer[2];
					byte[] m = new byte[TOR_CELL_LENGTH];
					if (type == 5) { // Open
						long opener = 0;
						for (int i = 3; i < 7; i++) {
							opener = (opener << 8) + (buffer[i] & 0xff);
						}
						long opened = 0;
						for (int i = 7; i < 11; i++) {
							opened = (opened << 8) + (buffer[i] & 0xff);
						}
						System.out.println(nodeName + " Received message: OPEN => {openerAid:" + opener + ", openedAid:" + opened + "}");
						Tor61NodeInfo info = new Tor61NodeInfo(src.getInetAddress(), src.getLocalPort(), opener + "");
						if (serviceData == (int) opened && !torSockets.containsKey(info)) {
							torSockets.put(info, src);
						} else {
							// Received this message even though wrong address
							System.out.println(nodeName + " Received incorrect message. {circId: " + circId +", type: " + type + "}");
						}
						// Reply with "opened" message
						m[0] = (byte) 0;
						m[1] = (byte) 0;
						m[2] = (byte) 0x06;
						for (int i = 3; i < TOR_CELL_LENGTH; i ++) { // This includes agent id of opener and opened, and padding
							m[i] = buffer[i];
						}
						aidToSocket.put(opener, src);
						socketToAid.put(src, opener);
					} else if (type == 1) { // Create
						System.out.println(nodeName + " Received message: CREATE => {circuitId:" + circId + "}");
						// Reply with "CREATED"
						m[0] = buffer[0];
						m[1] = buffer[1];
						m[2] = (byte) 0x02;
						for (int i = 3; i < TOR_CELL_LENGTH; i ++) {
							m[i] = buffer[i];
						}
						System.out.println(nodeName + " Sending message: CREATED");
						long openerAid = socketToAid.get(src);
						// Storing route from src to (-1,-1) because we are at the end point
						// We are not adding the reverse of this because we are definitely not the starting point of the circuit
						routingTable.addRoute(new RouterCircuit(openerAid, circId), new RouterCircuit(-1,-1));
					} else if (type == 4) { // Destroy

					} else if (type == 3) { // Relay
						long agentId = -1;
						if (socketToAid.containsKey(src)) { // allows us to get the agent id of the node who send this message
							agentId = socketToAid.get(src);
						}
						int streamId = 0;
						// convert the stream id bytes to a value
						for (int i = 3; i < 5; i++) {
							streamId = (streamId << 8) + (buffer[i] & 0xff);
						}
						RouterCircuit source = new RouterCircuit(agentId, circId);
						RouterCircuit dest = routingTable.getDest(source);
						if (!dest.equals(new RouterCircuit(-1, -1))) { // non end points will forward
							relayForward(source, dest, buffer);
						} else { // at the end point
							int bodyLength = 0;
							for (int i = 11; i < 13; i++) {
								bodyLength = (bodyLength << 8) + (buffer[i] & 0xff);
							}
							int relayCmd = buffer[13];
							System.out.println("relayCmd: " + relayCmd);
							if (relayCmd == 1) { // BEGIN
								System.out.println(nodeName + " Received message: RELAY BEGIN");
								String serverAddr = "";
								for (int i = 0; i < bodyLength - 1; i ++) { // Don't read null terminator
									serverAddr += (char) buffer[14 + i];
								}
								//System.out.println(serverAddr);
								String[] serverAddrString = serverAddr.split(":");
								// Open tcp connection with web server
								Socket webServerSocket = new Socket(serverAddrString[0], Integer.parseInt(serverAddrString[1]));
								webServerSocket.setKeepAlive(true);
								
								sidToServerSocket.put(streamId, webServerSocket);
								webServerSocketToSid.put(webServerSocket, streamId);

								// add a new blocking queue associated with this socket to the router side so it can write to the queue
								// cells that it wants to forward to this socket
								addNewQueueForSocket(webServerSocket);

								// Start a writer thread that will constantly read from a blocking queue and write to the socket
								makeWriter(webServerSocket);

								makeServerListener(webServerSocket);
								// Successfully established tcp connection with web server
								// add an entry to the response routing table that would allow us to send a response back to the node who
								// sent this relay message via the same circuit
								responseRoutingTable.put(streamId, new RouterCircuit(agentId, circId));
								// Send back "Connected" response
								connected(streamId, circId, src);
							} else if (relayCmd == 2) { // DATA CELL
								System.out.println(nodeName + " Received message: RELAY DATA CELL");
								Tor61Cell cell = new Tor61Cell(buffer);
								writeCellToQueueForSocket(sidToServerSocket.get(streamId), cell);
							} else if (relayCmd == 3) {

							} else if (relayCmd == 6) {
								String body = "";
								int count = 0;
								char c;
								int indexOfNull = -1;
								long destAid = 0;
								while (count < bodyLength) {
									if (((c = (char)buffer[14 + count]) != '\0') && indexOfNull == -1) { // body up to the '\0'
										body += c;
									} else if (((c = (char)buffer[14 + count]) != '\0') && indexOfNull != -1) { // build up agent id after '\0'
										for (int i = 0; i < 4; i++) {
											destAid = (destAid << 8) + (buffer[i + 14 + count] & 0xff);
										}
										break;
									} else { // set the index of the '\0'
										indexOfNull = count;
										System.out.println(indexOfNull);
									}
									count ++;
								}
								String destAddr = body.substring(0, indexOfNull).split(":")[0];
								int destPort = Integer.parseInt(body.substring(0, indexOfNull).split(":")[1]);
								System.out.println(nodeName + " Received message: RELAY EXTEND to" + destAid);
								// Once we get relay extend at end point, connect with new node
								connect(new Tor61NodeInfo(InetAddress.getByName(destAddr), destPort, Long.toString(destAid)),
										Long.toString(this.serviceData), source);
							} else {
								System.out.println(nodeName + " Error: Listener class received response.");
							}
						}
					}
					//System.out.println("Writing...");
					outputStream.write(m);
					outputStream.flush();
				}
				System.out.println(nodeName + " Exit loop 2, shouldnt be here.");///////////////////////////////////////
			} catch (IOException e) {
				e.printStackTrace();
				//System.out.println("Failed to get socket.");
				//System.exit(1);
			}
		}
	}

	/**
	 * This will read from a blocking queue that is associated with the given socket (in router side), and write to the given socket.
	 */
	class Writer implements Runnable {
		Socket socket;

		public Writer(Socket client) {
			this.socket = client;
		}

		@Override
		public void run() {
			while (true) {
				Tor61Cell cell = takeFromQueue(this.socket); // this call will block until an element is returned

				if (cell != null) { // if the wait for a non emtpy queue was interrupted
					// extra the byte array message and forward it to the socket
					byte[] message = cell.data;

					// Extract body(http request string) and put write to socket
					int bodyLength = 0;
					for (int i = 11; i < 13; i++) {
						bodyLength = (bodyLength << 8) + (message[i] & 0xff);
					}

					byte[] body = new byte[bodyLength];

					for (int i = 0; i < bodyLength; i++) { // copy body of message into body array
						body[i] = message[i + 14];
					}

					OutputStream outputStream =  null;
					try {
						outputStream = this.socket.getOutputStream();
						outputStream.write(body);
						outputStream.flush();
						System.out.println("Writing " + bodyLength + " bytes to server");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/*
	 * 
	 */
	public void makeWriter(Socket socket) {
		Writer w = new Writer(socket);
		Thread t = new Thread(w);
		t.start();
	}

	public void makeServerListener(Socket socket) {
		ServerListener sl = new ServerListener(socket);
		Thread t = new Thread(sl);
		t.start();
	}

	class ServerListener implements Runnable {
		Socket server;
		public ServerListener(Socket server) {
			this.server = server;
		}

		@Override
		public void run() {
			InputStream inputStream = null;
			try{
				int streamId = webServerSocketToSid.get(server);
				RouterCircuit start = responseRoutingTable.get(streamId);
				// Convert entire request to relay data cells and send them towards web server
				byte[] cellBody = new byte[TOR_CELL_LENGTH - 14]; // take away the space for relay message headers
				inputStream = server.getInputStream();
				int len = 0;
				while ((len = inputStream.read(cellBody, 0, TOR_CELL_LENGTH - 14)) >= 0) { // len = -1 if done
					System.out.println("Response len: " + len);
					relayDataCell(start, streamId, cellBody, len);
				}

				// Send relay end since we're done with this response
				//relayEnd(streamId);

				sidToServerSocket.remove(streamId);
				webServerSocketToSid.remove(server);
				responseRoutingTable.remove(streamId);
				socketToQueue.remove(server);
			} catch (IOException e) {
				System.out.println("Failed to get socket.");
				System.exit(1);
			} finally {
				try {
					if (inputStream != null) {
						inputStream.close();
					}
					if (server != null) {
						server.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}