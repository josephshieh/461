import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
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

				// add a new blocking queue associated with this socket to the router side so it can write to the queue
				// cells that it wants to forward to this socket
				router.addNewQueueForSocket(client);

				// Now that we have a client to to communicate with, create new thread
				ClientListener l = new ClientListener(client);
				Thread t = new Thread(l);
				t.start();

				// Start a writer thread that will constantly read from a blocking queue and write to the socket
				/*Writer w = new Writer(client);
				Thread t2 = new Thread(w);
				t2.start();*/
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
				//OutputStream outputStream = socket.getOutputStream();
				BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				String inputLine, outputLine = "";
				boolean first = true;
				String temp, hostAddr = "";
				int port = 80;
				int streamId = -1;
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
					} else if (temp.startsWith("connection:")) {
						// Make connection close instead of
						outputLine += "Connection: close" + EOF;
					} else if (temp.equals("")) {
						// End of request's HTTP header
						outputLine += EOF;
						InetAddress destAddr = InetAddress.getByName(hostAddr);
						// Convert entire request to relay data cells and send them towards web server
						byte[] cellBody = new byte[TOR_CELL_LENGTH - 14]; // take away the space for relay message headers
						InputStream requestStream = null;
						DataInputStream dis = null;
						requestStream = new ByteArrayInputStream(outputLine.getBytes());;
						dis = new DataInputStream(requestStream);
						RouterCircuit start = new RouterCircuit(-1, -1);
						while (dis.available() >= (TOR_CELL_LENGTH - 14)) { // if anything is available, its guaranteed to be 512 bytes
							//try {
							dis.readFully(cellBody);
							//} catch (EOFException e) { // When other node closes
							//System.out.println("Caught eof exception");
							//continue;
							//}
							router.relayDataCell(start, streamId, cellBody, TOR_CELL_LENGTH - 14);
						}
						// read the remainder of the requestStream
						int count = requestStream.available(); // count the available bytes form the input stream
						byte[] cellEnd = new byte[count];
						dis.read(cellEnd);
						router.relayDataCell(start, streamId, cellEnd, count);

						//SendAndReceive s = new SendAndReceive(destAddr, port, outputLine);
						//Thread t = new Thread(s);
						//t.start();
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

		/**
		 * This is for redirecting requests to the web server and receiving responses back.
		 */
		class SendAndReceive implements Runnable {
			Socket sendAndReceive;
			String outputLine;

			public SendAndReceive(/*InetAddress destAddr, int port, */String outputLine, Socket webServer) {
				//try {
				//sendAndReceive = new Socket(destAddr, port);
				sendAndReceive = webServer;
				//} catch (IOException e) {
				//System.out.println("Failed to find socket on web server.");
				//}
				this.outputLine = outputLine;
			}

			@Override
			public void run() {
				try {
					// Redirect the packet to the web server
					InputStream inputStream = sendAndReceive.getInputStream();
					PrintWriter outputStream = new PrintWriter(sendAndReceive.getOutputStream(), true);
					// Write to web server as string
					outputStream.write(outputLine);
					outputStream.flush();
					// Read response as bytes
					byte[] buffer = new byte[1000];
					//byte[] bufferFinal = new byte[1000];
					int len;
					while((len = inputStream.read(buffer)) > 0) {
						/*String temp = new String(buffer);
						boolean changed = false;
						if (temp.contains("Connection: keep-alive")) {
							System.out.println("HAHAHA");
							temp.replaceAll("Connection: keep-alive", "Connection: close");
							changed = true;
						}
						bufferFinal = temp.getBytes();
						if (changed) {
							this.clientOutputStream.write(bufferFinal, 0, len-5);
						} else {
							this.clientOutputStream.write(bufferFinal, 0, len);
						}*/
						// Write to client as bytes as well
						//this.clientOutputStream.write(buffer, 0, len);
					}
					//this.clientOutputStream.flush();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}

		}
	}
}


