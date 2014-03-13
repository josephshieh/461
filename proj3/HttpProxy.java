import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

/**
 * Joseph Shieh, 1031718, josephs2@uw.edu
 * Sergey Naumets, 1025573, snaumets@uw.edu
 */
public class HttpProxy implements Runnable {
	public static final int TOR_CELL_LENGTH = 512;

	ServerSocket proxyServerSocket;
	int port;
	Tor61Router router;

	public HttpProxy(int port, Tor61Router router) {
		this.port = port;
		this.router = router;
	}

	@Override
	public void run() {
		proxyServerSocket = null;
		try {
			proxyServerSocket = new ServerSocket(port);
			System.out.println("HTTP Proxy is up on port: " + port);
		} catch (IOException e) {
			System.out.println("Failed to create server socket on port: " + port);
			System.exit(-1);
		}

		while (true) { // Listen forever, terminated by Ctrl-C
			try {
				Socket client = proxyServerSocket.accept();
				client.setKeepAlive(true);

				// add a new blocking queue associated with this socket to the router side so it can write to the queue
				// cells that it wants to forward to this socket
				router.addNewQueueForSocket(client);

				// Now that we have a client to to communicate with, create new thread
				ClientListener l = new ClientListener(client);
				Thread t = new Thread(l);
				t.start();

				// Start a writer thread that will constantly read from a blocking queue and write to the socket
				router.makeWriter(client);

			} catch (IOException e) {
				System.out.println("Failed to accept connection.");
			}
		}
	}

	/**
	 * This is for listening to a specific client's request, and create a new thread to redirect the request
	 * to the web server.
	 */
	class ClientListener implements Runnable {
		Socket socket;
		public static final String EOF = "\r\n"; // CR LF String
		public ClientListener(Socket client) {
			this.socket = client;
		}

		@Override
		public void run() {
			try{
				BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String inputLine, outputLine = "";
				boolean first = true;
				String temp, hostAddr = "";
				int port = 80;
				int streamId = -1;
				boolean streamSet = false;
				while ((inputLine = inputStream.readLine()) != null) {
					if (first){
						// Print the first line of the request
						System.out.println(inputLine);
						first = false;
					}
					temp = inputLine.toLowerCase();
					if (temp.startsWith("host:")) {
						// Get the host address
						String host = inputLine.split(": ")[1];
						// If the host address has a port, it will be in the form of hostAddr:port
						String[] hostString = host.split(":");
						if (hostString.length == 2) {
							port = Integer.parseInt(hostString[1]);
						}
						hostAddr = hostString[0];
						outputLine += inputLine + EOF;

						if (!streamSet) {
							Random r = new Random();
							streamId = r.nextInt(65536);
							while (router.sidToClientSocketContainsKey(streamId)){
								streamId = r.nextInt(65536);
							}
							router.addSidToClientSocket(streamId, socket);
							// Send a relay begin cell
							router.relayBegin(streamId, hostAddr + ":" + port);
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					} else if (temp.startsWith("connection:")) {
						// Make connection close instead of
						outputLine += "Connection: close" + EOF;
					} else if (temp.equals("")) {
						// End of request's HTTP header
						outputLine += EOF;
						// Convert entire request to relay data cells and send them towards web server
						System.out.println("Browser Request: ");
						System.out.println(outputLine);
						byte[] cellBody = new byte[TOR_CELL_LENGTH - 14]; // take away the space for relay message headers
						InputStream requestStream = null;
						DataInputStream dis = null;
						requestStream = new ByteArrayInputStream(outputLine.getBytes());;
						dis = new DataInputStream(requestStream);
						RouterCircuit start = new RouterCircuit(-1, -1);
						while (dis.available() >= (TOR_CELL_LENGTH - 14)) { // if anything is available, its guaranteed to be 512 bytes
							dis.readFully(cellBody);
							router.relayDataCell(start, streamId, cellBody, TOR_CELL_LENGTH - 14);
						}
						// read the remainder of the requestStream
						int count = dis.available(); // count the available bytes form the input stream
						byte[] cellEnd = new byte[count];
						dis.readFully(cellEnd);
						router.relayDataCell(start, streamId, cellEnd, count);
					} else { // not host or connection
						outputLine += inputLine + EOF;
					}
					// manually close connection or wait for timeout
				}
			} catch (IOException e) {
				System.out.println("Failed to get socket.");
				System.exit(1);
			}
		}
	}
}

