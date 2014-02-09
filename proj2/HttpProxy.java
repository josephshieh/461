import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
			String temp, host, connection;
			while ((inputLine = inputStream.readLine()) != null) {
				// TODO: Look for CR-LF to terminate loop
				System.out.println(inputLine);
				temp = inputLine.toLowerCase();
				if (temp.startsWith("host:")) {
					host = inputLine.split(": ")[1];
					outputLine += inputLine;
					//System.out.println("young fuck: " + host);
				} else if (temp.startsWith("connection:")) {
					connection = inputLine;
					outputLine += "Connection: close";
					//System.out.println("young con: " + temp);
				} else { // not host or connection
					outputLine += inputLine;
				}
				outputLine += (char) 13 + ""; // CR string
				outputLine += (char) 10 + ""; // LF string

				System.out.println("Output so far:");
				System.out.println(outputLine);
				// break;
				// manually close connection or wait for timeout
			}
		} catch (IOException e) {

		}
	}

}