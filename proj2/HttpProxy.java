import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

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
				Thread t = new Thread(l);
				t.start();
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
		try{
			OutputStream outputStream = socket.getOutputStream();
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String inputLine, outputLine = "";
			boolean first = true;
			String temp, hostAddr = "";
			int port = 80;
			String eof = "\r\n"; // CR LF String
			while ((inputLine = inputStream.readLine()) != null) {
				if (first){
					// Print the first line of the request
					System.out.println(inputLine);
					first = false;
				}
				//System.out.println(inputLine);
				temp = inputLine.toLowerCase();
				if (temp.startsWith("host:")) {
					String host = inputLine.split(": ")[1];
					String[] hostString = host.split(":");
					if (hostString.length == 2) {
						port = Integer.parseInt(hostString[1]);
					}
					hostAddr = hostString[0];
					outputLine += inputLine + eof;
				} else if (temp.startsWith("connection:")) {
					outputLine += "Connection: close" + eof;
				} else if (temp.equals("")) {
					//System.out.println("Last Line!");
					outputLine += eof;
					//System.out.println(hostAddr);
					//System.out.println(port);
					InetAddress destAddr = InetAddress.getByName(hostAddr);
					SendAndReceive s = new SendAndReceive(destAddr, port, outputLine, outputStream);
					Thread t = new Thread(s);
					t.start();
				} else { // not host or connection
					outputLine += inputLine + eof;
				}
				// manually close connection or wait for timeout
			}
		} catch (IOException e) {
			System.out.println("Failed to get socket.");
			System.exit(1);
		}
	}

}

class SendAndReceive implements Runnable {
	Socket sendAndReceive;
	String outputLine;
	OutputStream clientOutputStream;

	public SendAndReceive(InetAddress destAddr, int port, String outputLine, OutputStream clientOutputStream) {
		try {
			sendAndReceive = new Socket(destAddr, port);
		} catch (IOException e) {
			System.out.println("Failed to find socket on web server.");
		}
		this.outputLine = outputLine;
		this.clientOutputStream = clientOutputStream;
	}

	@Override
	public void run() {
		try {
			// Redirect the packet to the web server
			//System.out.println(outputLine);
			InputStream inputStream = sendAndReceive.getInputStream();
			PrintWriter outputStream = new PrintWriter(sendAndReceive.getOutputStream(), true);
			// Write to web server as string
			outputStream.write(outputLine);
			outputStream.flush();
			// Read response as bytes
			byte[] buffer = new byte[1000];
			int len;
			while((len = inputStream.read(buffer)) > 0) {
				// Write to client as bytes as well
				this.clientOutputStream.write(buffer, 0, len);
			}
			this.clientOutputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}