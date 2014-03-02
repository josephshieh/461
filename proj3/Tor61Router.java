import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class Tor61Router implements Runnable {
	public static final int TOR_CELL_LENGTH = 512;

	Map<Tor61NodeInfo, Socket> torSockets;
	int port;
	int serviceData;

	public Tor61Router(int serviceData) {
		this.torSockets = new HashMap<Tor61NodeInfo, Socket>();
		this.serviceData = serviceData;
	}

	public void connect(Tor61NodeInfo node, String serviceData) {
		if (!torSockets.containsKey(node)) {
			// Open a new TCP connection
			Socket send = null;
			try {
				send = new Socket(node.address.toString().substring(1), node.port);
				torSockets.put(node, send);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// Can only assign odd numbers to circuit id
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
			System.out.println(m[3]);
			System.out.println(m[4]);
			System.out.println(m[5]);
			System.out.println(m[6]);
			byte[] opened = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
					.putLong(Long.parseLong(node.serviceData)).array();
			m[7] = opened[4]; // Service data
			m[8] = opened[5];
			m[9] = opened[6];
			m[10] = opened[7];
			System.out.println(m[7]);
			System.out.println(m[8]);
			System.out.println(m[9]);
			System.out.println(m[10]);
			Arrays.fill(m, 11, 512, (byte) 0); // pad rest of array with zeros
			PrintWriter outputStream = null;
			InputStream inputStream = null;
			try {
				inputStream = send.getInputStream();
				outputStream = new PrintWriter(send.getOutputStream(), true);
				// Write to web server as string
				outputStream.write(new String(m));
				outputStream.flush();
				// Read response from the "opened"
				byte[] buffer = new byte[TOR_CELL_LENGTH];
				int len;
				System.out.println("abc");
				while((len = inputStream.read(buffer)) > 0) {
					System.out.println("ha");
					System.out.println(buffer[3]);
					System.out.println(buffer[4]);
					System.out.println(buffer[5]);
					System.out.println(buffer[6]);
					System.out.println(buffer[7]);
					System.out.println(buffer[8]);
					System.out.println(buffer[9]);
					System.out.println(buffer[10]);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
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
		Socket socket;
		public static final String EOF = "\r\n"; // CR LF String
		public Listen(Socket client) {
			this.socket = client;
		}

		@Override
		public void run() {
			try{
				OutputStream outputStream = socket.getOutputStream();
				InputStream inputStream = socket.getInputStream();
				byte[] buffer = new byte[TOR_CELL_LENGTH];
				int len;
				// Reading input
				while((len = inputStream.read(buffer)) > 0) { // should always read in 512 bytes
					byte[] circIdBytes = new byte[2];
					circIdBytes[0] = buffer[0];
					circIdBytes[1] = buffer[1];
					int circId = ByteBuffer.wrap(circIdBytes).getInt();
					System.out.println("Circuit id: " + circId);
					int type = buffer[2];
					if (type == 5) { // Open
						long opener = 0;
						for (int i = 3; i < 7; i++) {
							opener = (opener << 8) + (buffer[5 + i] & 0xff);
						}
						long opened = 0;
						for (int i = 7; i < 11; i++) {
							opened = (opened << 8) + (buffer[5 + i] & 0xff);
						}
						Tor61NodeInfo info = new Tor61NodeInfo(socket.getInetAddress(), socket.getLocalPort(), opener + "");
						if (serviceData == (int) opened && !torSockets.containsKey(info)) {
							torSockets.put(info, socket);

						} else {
							// Received this message even though wrong address

						}
					} else if (type == 6) { // Opened

					} else if (type == 7) { // Open failed

					} else if (type == 1) { // Create

					} else if (type == 2) { // Created

					} else if (type == 8) { // Create failed

					} else if (type == 4) { // Destroy

					} else if (type == 3) { // Relay

					} else { // Invalid message type

					}
				}
			} catch (IOException e) {
				System.out.println("Failed to get socket.");
				System.exit(1);
			}
		}
	}
}
