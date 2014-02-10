import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Joseph Shieh, 1031718, josephs2@uw.edu
 * Sergey Naumets, ,
 * CSE 461 Networks Project 2, Create an HTTP proxy that handles requests from the client and redirects it to the server.
 */
public class HttpProxy {

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Wrong number of arguments given.");
			usage();
		}

		int port = -1;
		try {
			port = Integer.parseInt(args[0]); // the second argument will be a service port number
		} catch (NumberFormatException e) {
			// the given argument wasnt an integer, so we exit
			System.out.println("The given port was not an integer.");
			usage();
		}

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
				Thread t1 = new Thread(l);
				t1.start();

			} catch (IOException e) {
				System.out.println("Failed to accept connection.");
			}
		}

	}

	public static void usage() {
		System.out.println("Usage: java HttpProxy <service port>");
		System.exit(1);
	}

}

class Listen implements Runnable {
	Socket socket;

	public Listen(Socket client) {
		this.socket = client;
	}

	@Override
	public void run() {
		try {
			PrintWriter outputStream = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String inputLine, outputLine = "";
			boolean first = true;
			String temp, hostAddr = "";
			int port = 80;
			while ((inputLine = inputStream.readLine()) != null) {
				if (first){
					// Print the first line of the request
					System.out.println(inputLine);
					first = false;
				}
				// TODO: Look for CR-LF to terminate loop
				System.out.println(inputLine);
				temp = inputLine.toLowerCase();
				if (temp.startsWith("host:")) {
					String host = inputLine.split(": ")[1];
					String[] hostString = host.split(":");
					if (hostString.length == 2) {
						port = Integer.parseInt(hostString[1]);
					}
					hostAddr = hostString[0];
					outputLine += inputLine;

					//System.out.println("young fuck: " + host);
				} else if (temp.startsWith("connection:")) {
					String connection = inputLine;
					outputLine += "Connection: close";
					//System.out.println("young con: " + temp);
				} else if (temp.equals("")) {
					System.out.println("Last Line!");
					outputLine += inputLine;
					SendAndReceive s = new SendAndReceive(outputLine, hostAddr, port);
					Thread t1 = new Thread(s);
					t1.start();
				} else { // not host or connection
					outputLine += inputLine;
				}
				outputLine += (char) 13 + ""; // CR string
				outputLine += (char) 10 + ""; // LF string
				//System.out.println("Output so far:");
				//System.out.println(outputLine);
				// break;
				// manually close connection or wait for timeout
			}

		} catch (IOException e) {

		}
	}

}

class SendAndReceive implements Runnable {
	DatagramSocket sendAndReceive;
	String outputLine;
	String hostAddr;
	int port;

	public SendAndReceive(String outputLine, String hostAddr, int port)  {
		try {
			sendAndReceive = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Failed to create send and receive socket");
			System.exit(1);
		}
		this.outputLine = outputLine;
		this.hostAddr = hostAddr;
		this.port = port;
	}

	@Override
	public void run() {

		try {
			// Redirect the packet to the web server
			System.out.println("Sending to ... hostaddr: " + hostAddr);
			System.out.println(outputLine);
			System.out.println("End of request");
			InetAddress destAddr = InetAddress.getByName(hostAddr);
			DatagramPacket packet = new DatagramPacket(outputLine.getBytes(), outputLine.getBytes().length, destAddr, port);
			System.out.println("a");
			sendAndReceive.send(packet);
			System.out.println("b");
			// Receive response from the web server
			byte[] buffer = new byte[1000];
			DatagramPacket response = new DatagramPacket(buffer, buffer.length);
			sendAndReceive.receive(response);
			System.out.println("c");
			String responseString = new String(response.getData());
			System.out.println("Response string: " + responseString);
			//outputStream.print();
		} catch (IOException e) {
			e.printStackTrace();
		}


	}

}