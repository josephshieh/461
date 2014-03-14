/**
 *  Joseph Shieh, Sergey Naumets
 *
 */
public class BitCoins {

	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("Wrong number of arguments given.");
			usage();
		}
		String inFilename = args[2];
		String outFilename = args[3];

	}

	public static void usage() {
		System.out.println("Usage: java BitCoins <hostname> <port> <in-filename> <out-filename>");
		System.exit(1);
	}
}
