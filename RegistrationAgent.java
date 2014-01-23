/* Name: Sergey Naumets, Email: snaumets@uw.edu, SID#: 1025573
 * Name: Joseph Shieh, Email: , SID#:
 * CSE461 Project1, Winter 2014
 * 
 * This class will act as a client to a registration service, which will be a discovery mechanism that allows nodes, like
 * this class, to find each other. It is really just an agent that supports another application, that will also be called
 * a service. It will be able to send certain requests and receive responses, which will allow it to communicate with the 
 * registration server.
 */

import java.net.*;
import java.io.*;

public class RegistrationAgent {
    public static void main(String args[]) throws Exception {
        if (args.length == 2) {
            String hostname = args[0]; // the registration service host name
            int port = -1;
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
        
        // Here, we will accept commands from stdin that cause it to send messages to the registration service 
        // identified by the command line arguments, to read its responses the service sends, and to print 
        // appropriate messages about the result of the interaction.
        String prompt = "Enter r(egister), u(nregister), f(etch), p(robe), or q(uit):";
        System.out.println(prompt);
        // This reader will read a line of input at a time from stdin and send it to the client
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in)); 
        String command = "";
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
        	
        	if (command.startsWith("r")) {
        		
        	} else if (command.startsWith("u")) {
        		
        	} else if (command.startsWith("f")) {
        		
        	} else if (command.startsWith("p")) {
        		
        	} else {
        		System.out.println("The issued command is not recognized.");
        		System.out.println("Command options: ");
        		System.out.println("	r portnum data serviceName ==> Send Register message to the server.");
        		System.out.println("	u portnum ==> Send Unregister message to registration service.");
        		System.out.println("	f <name prefix> ==> Send a Fetch message to registration service");
        		System.out.println("	p ==> Send Probe to registration service");
        		System.out.println("	q ==> Quit execution");
        	}
        	System.out.println(prompt);
        }
    }
}
