import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

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
		byte[] fileBytes = read(inFilename);
		int i = 0;
		// Read
		readGenesisBlock(fileBytes);
		// Genesis block size
		//i += 290; // 82 + 4 + 204
		testMerkleTree();
		while (i < fileBytes.length) {
			// Read transactions
		}
	}


	public static void readGenesisBlock(byte[] fileBytes) {
		// Block Header
		byte[] header = getByteArr(fileBytes, 0, 82);
		byte[] dHash = dHash(header);
		System.out.println("Block: dHash (name) = " + bytesToString(dHash, 0, dHash.length));
		int version = bytesToInt(fileBytes, 0);
		System.out.println("   Version number: " + version);
		System.out.println("   Previous Block Reference: " + bytesToString(fileBytes, 4, 32));
		System.out.println("   Merkle root: " + bytesToString(fileBytes, 36, 32));
		int creationTime = bytesToInt(fileBytes, 68);
		System.out.println("   Creation time: " + creationTime);
		System.out.println("   Difficulty: " + bytesToShort(fileBytes, 72));
		System.out.println("   Nonce: " + bytesToLong(fileBytes, 74));
		System.out.println("   Header packedBytes: " + bytesToString(header, 0, 82));

		// Transaction Count
		int transactionCount = bytesToInt(fileBytes, 82);
		System.out.println("   Genesis transaction count: " + transactionCount);

		// Genesis Transaction
		byte[] transaction = getByteArr(fileBytes, 86, 40);
		byte[] transactionDHash = dHash(transaction);
		System.out.println("   Transaction: dHash (name) = " +
				bytesToString(transactionDHash, 0, transactionDHash.length));
		System.out.println("      nInputs: " + bytesToShort(fileBytes, 86));
		System.out.println("      nOutputs: " + bytesToShort(fileBytes, 88));
		System.out.println("      TxOut");
		System.out.println("         value: " + bytesToInt(fileBytes, 90));
		byte[] dHashPublicKeyBytes = dHash(getByteArr(fileBytes, 94, 32));
		System.out.println("         dHashPublicKey: " +
				bytesToString(dHashPublicKeyBytes, 0 , dHashPublicKeyBytes.length));
	}

	public static void testMerkleTree() {
		String a = "a";
		String b = "five";
		String c = "word";
		String d = "input";
		String e = "example";
		System.out.println(a + " dHash value = " + bytesToString(dHash(a.getBytes()), 0, 32));
		System.out.println(b + " dHash value = " + bytesToString(dHash(b.getBytes()), 0, 32));
		System.out.println(c + " dHash value = " + bytesToString(dHash(c.getBytes()), 0, 32));
		System.out.println(d + " dHash value = " + bytesToString(dHash(d.getBytes()), 0, 32));
		System.out.println(e + " dHash value = " + bytesToString(dHash(e.getBytes()), 0, 32));
		List<byte[]> leafNodes = new ArrayList<byte[]>();
		leafNodes.add(dHash(a.getBytes()));
		leafNodes.add(dHash(b.getBytes()));
		leafNodes.add(dHash(c.getBytes()));
		leafNodes.add(dHash(d.getBytes()));
		leafNodes.add(dHash(e.getBytes()));
		System.out.println("Merkle root: " + bytesToString(merkleTree(leafNodes),0, 32));
	}

	// Leaf nodes are hashes of the data items
	public static byte[] merkleTree(List<byte[]> leafNodes) {
		if (leafNodes.size() == 0) {
			throw new IllegalArgumentException();
		}
		List<byte[]> list1 = leafNodes;
		List<byte[]> list2 = new ArrayList<byte[]>();
		while (list1.size() > 1) {
			for (int i = 0; i < list1.size(); i += 2) {
				byte[] child1 = list1.get(i);
				byte[] child2;
				if (i + 1 < list1.size()) {
					// If there's two items still
					child2 = list1.get(i + 1);
				} else {
					// If there's only one item left
					child2 = child1;
				}
				byte[] combined = new byte[child1.length * 2];
				for (int j = 0; j < child1.length; j++) {
					combined[j] = child1[j];
					combined[j + child1.length] = child2[j];
				}
				list2.add(dHash(combined));
			}
			list1 = list2;
			list2 = new ArrayList<byte[]>();
		}
		return list1.get(0);
	}

	// SHA256(SHA256(buffer))
	public static byte[] dHash(byte[] buffer) {
		MessageDigest sha256 = null;
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return sha256.digest(sha256.digest(buffer));
	}

	public static byte[] getByteArr(byte[] arr, int start, int len) {
		byte[] buffer = new byte[len];
		for (int i = 0; i < buffer.length; i ++) {
			buffer[i] = arr[start + i];
		}
		return buffer;
	}

	public static int bytesToInt(byte[] arr, int start) {
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	public static short bytesToShort(byte[] arr, int start) {
		byte[] bytes = new byte[2];
		for (int i = 0; i < 2; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
	}

	public static long bytesToLong(byte[] arr, int start) {
		byte[] bytes = new byte[8];
		for (int i = 0; i < 8; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	public static String bytesToString(byte[] arr, int start, int len) {
		StringBuffer result = new StringBuffer();
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i ++) {
			bytes[i] = arr[start + i];
			result.append(String.format("%02x", bytes[i]));
		}
		return result.toString();
	}



	public static void usage() {
		System.out.println("Usage: java BitCoins <hostname> <port> <in-filename> <out-filename>");
		System.exit(1);
	}
	/** We borrowed the read() and write() from http://www.javapractices.com/topic/TopicAction.do?Id=245 **/

	/** Read the given binary file, and return its contents as a byte array.*/
	public static byte[] read(String aInputFileName){
		System.out.println("Reading in binary file named : " + aInputFileName);
		File file = new File(aInputFileName);
		System.out.println("File size: " + file.length());
		byte[] result = new byte[(int)file.length()];
		try {
			InputStream input = null;
			try {
				int totalBytesRead = 0;
				input = new BufferedInputStream(new FileInputStream(file));
				while(totalBytesRead < result.length){
					int bytesRemaining = result.length - totalBytesRead;
					//input.read() returns -1, 0, or more :
					int bytesRead = input.read(result, totalBytesRead, bytesRemaining);
					if (bytesRead > 0){
						totalBytesRead = totalBytesRead + bytesRead;
					}
				}

				/*the above style is a bit tricky: it places bytes into the 'result' array;
	         'result' is an output parameter;
	         the while loop usually has a single iteration only.
				 */
				System.out.println("Num bytes read: " + totalBytesRead);
			}
			finally {
				System.out.println("Closing input stream.");
				input.close();
			}
		}
		catch (FileNotFoundException ex) {
			System.out.println("File not found.");
		}
		catch (IOException ex) {
			System.out.println(ex);
		}
		return result;
	}

	/**
	   Write a byte array to the given file.
	   Writing binary data is significantly simpler than reading it.
	 */
	void write(byte[] aInput, String aOutputFileName){
		System.out.println("Writing binary file...");
		try {
			OutputStream output = null;
			try {
				output = new BufferedOutputStream(new FileOutputStream(aOutputFileName));
				output.write(aInput);
			}
			finally {
				output.close();
			}
		}
		catch(FileNotFoundException ex){
			System.out.println("File not found.");
		}
		catch(IOException ex){
			System.out.println(ex);
		}
	}

	private static RSAPublicKey publicKeyFromBytes(byte[] keyBytes) {
		Object o;
		Reader fRd = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(keyBytes)));
		PEMParser pemRd = new PEMParser(fRd);

		try {
			while ((o = pemRd.readObject()) != null) {
				if (o instanceof SubjectPublicKeyInfo) {
					JcaPEMKeyConverter myConverter = new JcaPEMKeyConverter();
					RSAPublicKey myKey = null;
					try {
						return (RSAPublicKey) myConverter.getPublicKey((SubjectPublicKeyInfo) o);
					} catch (PEMException e) {
						return null;
					}
				} else {
					return null;
				}
			}
		} catch (IOException e) {
			return null;
		}
		return null;
	}
}
