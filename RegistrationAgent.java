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
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
	
	private void waitForResponse(DatagramSocket socket, int expectedSeqNum) {
		byte[] recvData = new byte[1024];
		DatagramPacket packet = new DatagramPacket(recvData, recvData.length);
        // will wait forever until a packet is received
        try {
			socket.receive(packet);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
        // when we get here, we have received a packet with some data
        // print the data contained in the arriving packet
        String data = new String(packet.getData(), 0, packet.getLength());
        byte[] dataBytes = data.getBytes();
        if (dataBytes[0] != (byte) 0xC4 && dataBytes[1] != (byte) 0x61) {
        	System.out.println("Unknown message: Magic Number mismatch.");
        } else {
        	int seqNum = (int) dataBytes[2];
    		if (expectedSeqNum != seqNum) { // Do nothing if 
    			return;
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
        		System.out.println("FetchResponse, change this message");
        	} else if (dataBytes[3] == (byte) 0x07) { // ACK
        		System.out.println("Success");
        	}
        }
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
		
		String prompt = "Enter r(egister), u(nregister), f(etch), p(robe), or q(uit):";
		System.out.println(prompt);
		// This reader will read a line of input at a time from stdin and send it to the client
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String command = "";
		
		try {
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
				DatagramSocket serviceSocket = null;
				if (command.startsWith("r")) { //----- register -----------------------------------------------------
/////////////////////////////////////// TODO: send no more than max packet size
/////////////////////////////////////// TODO:if a packet is re-sent, all copies carry the originally assigned sequence number
/////////////////////////////////////// TODO:the sequence numbers (eventually) wrap.
					int msgLength = 0;
					String[] args = command.split(" ");
					if (args.length != 4) {
						usage("r");
						continue;
					}
					int portnum = Integer.parseInt(args[1]);
					if (serviceSocket == null) {
						try {
							serviceSocket = new DatagramSocket(portnum);
						} catch (BindException e) {
							System.out.println("Can't reserve this port. Please try another port.");
							continue;
						}
					}
					String serviceData = args[2];
					String serviceName = args[3];
					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x01; // Register
					msgLength += 4;
					String ip;
					byte[] ipBytes;
					try {
						ip = InetAddress.getLocalHost().getHostAddress();
						ipBytes = ip.getBytes();
						m[4] = ipBytes[0]; // Service ip
						m[5] = ipBytes[1];
						m[6] = ipBytes[2];
						m[7] = ipBytes[3];
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
					DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
					
					// Create a thread that will listen server's probes
					Listen probeListen = new Listen(listenSocket);
					Thread probeThread = new Thread(probeListen);
					probeThread.start();
					try {
						sendSocket.send(packet);
						// Listen for server response; expecting an Registered message with lifetime of connection
						// Wait for response, verify sequence number
						waitForResponse(sendSocket, sequenceNum);
						this.sequenceNum ++;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if (command.startsWith("u")) { //----- unregister -----------------------------------------------------
					int msgLength = 0;
					String[] args = command.split(" ");
					if (args.length != 2) {
						usage("u");
						continue;
					}
					int portnum = Integer.parseInt(args[1]);
					if (sendSocket == null) {
						sendSocket = new DatagramSocket(portnum);
					}
					byte[] m = new byte[1000];
					m[0] = (byte) 0xC4;
					m[1] = (byte) 0x61;
					m[2] = (byte) sequenceNum;
					m[3] = (byte) 0x05; // Unregister
					msgLength += 4;
					String ip;
					byte[] ipBytes;
					try {
						ip = InetAddress.getLocalHost().getHostAddress();
						ipBytes = ip.getBytes();
						m[4] = ipBytes[0]; // Service ip
						m[5] = ipBytes[1];
						m[6] = ipBytes[2];
						m[7] = ipBytes[3];
						msgLength += 4;
					} catch (UnknownHostException e) {
						e.printStackTrace();
						System.exit(1);
					}
					m[8] = (byte) ((portnum >> 8) & 0xFF); // Service port number, will be in big endian order this way 
					m[9] = (byte) (portnum & 0xFF);
					msgLength += 2;
					DatagramPacket packet = new DatagramPacket(m, msgLength, this.destAddr, this.destPort);
					try {
						sendSocket.send(packet);
						// Wait for response, verify sequence number
						waitForResponse(sendSocket, sequenceNum);
						this.sequenceNum ++;
					} catch (IOException e) {
						e.printStackTrace();
					}

					// Listen for server response; expecting an ACK message
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
					try {
						sendSocket.send(packet);
						// Wait for response, verify sequence number
						waitForResponse(sendSocket, sequenceNum);
						this.sequenceNum ++;
					} catch (IOException e) {
						e.printStackTrace();
					}
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
					try {
						sendSocket.send(packet);
						// Wait for response, verify sequence number
						waitForResponse(sendSocket, sequenceNum);
						this.sequenceNum ++;
					} catch (IOException e) {
						e.printStackTrace();
					}

					// Listen for server response; expecting an ACK message
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
            if (sendSocket != null)
                sendSocket.close();
            if (listenSocket != null)
                listenSocket.close();
        }
	}
}


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
                // will wait forever until a packet is received
                socket.receive(packet);
                // when we get here, we have received a packet with some data
                // print the data contained in the arriving packet
                String data = new String(packet.getData(), 0, packet.getLength());
                byte[] dataBytes = data.getBytes();
                if (dataBytes[0] != (byte) 0xC4 && dataBytes[1] != (byte) 0x61) {
                	System.out.println("Unknown message: Magic Number mismatch.");
                } else {
                	if (dataBytes[3] == (byte) 0x06) { // Probe
                		int seqNum = (int) dataBytes[2];
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
            // This exception is expected, and is how we will cleanly exit this thread
            //System.out.println("SocketException: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
            return;
        }
	}
}
