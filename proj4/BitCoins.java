import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

/**
 *  Joseph Shieh, Sergey Naumets
 *
 */
public class BitCoins {
	static Map<String, List<OutputSpecifier>> txToOutputList;

	public static void main(String[] args) {
		Security.addProvider(new BouncyCastleProvider()); // For encrypting/decrypting
		if (args.length != 4) {
			System.out.println("Wrong number of arguments given.");
			usage();
		}

		String inFilename = args[2];
		String outFilename = args[3];
		byte[] fileBytes = read(inFilename);
		int i = 0;
		txToOutputList = new HashMap<String, List<OutputSpecifier>>();

		// Read Genesis Block info
		readGenesisBlock(fileBytes);
		System.out.println();
		// Transaction Count of the rest
		int transactionCount = bytesToInt(fileBytes, 126);
		System.out.println("Transaction count: " + transactionCount);

		int invalidTx = 0;
		List<byte[]> allTx = new ArrayList<byte[]>();
		i = 130; // Genesis stuff plus transaction count processed
		// First transaction at index 130
		List<Integer> notRsa = new ArrayList<Integer>();
		for (int j = 0; j < transactionCount; j++) {
			int startIndex = i;
			boolean invalid = false;
			int inputTotal = 0;
			int outputTotal = 0;
			System.out.println("Transaction # " + j + " ---------------------------------------------------------");
			short nInputs = bytesToShort(fileBytes, i);
			System.out.println("   nInputs: " + nInputs);
			byte[] nInputsBytes = getByteArr(fileBytes, i, 2);
			i += 2;

			List<byte[]> prevTxRefBytesList = new ArrayList<byte[]>();
			List<byte[]> prevTxOutputIndexBytesList = new ArrayList<byte[]>();
			List<byte[]> publicKeyLenBytesList = new ArrayList<byte[]>();
			List<byte[]> publicKeyBytesList = new ArrayList<byte[]>();
			byte[] prevTxRefBytes = null;
			byte[] prevTxOutputIndexBytes = null;
			byte[] publicKeyLenBytes = null;
			byte[] publicKeyBytes = null;
			int transactionSizeSoFar = nInputsBytes.length;

			// Process each input specifier
			byte[] decryptedSignature = null;
			for (int k = 0; k < nInputs; k++) {
				String prevTxRef = bytesToString(fileBytes, i, 32);
				System.out.println("   prevTxRef: " + prevTxRef);
				short prevTxOutputIndex = bytesToShort(fileBytes, i + 32);
				System.out.println("   prevTxOutputIndex: " + prevTxOutputIndex);
				String signature = bytesToString(fileBytes, i + 32 + 2, 128);
				System.out.println("   signature: " + signature);
				short publicKeyLen = bytesToShort(fileBytes, i + 32 + 2 + 128);
				String publicKey = bytesToString(fileBytes, i + 32 + 2 + 128 + 2, publicKeyLen);
				System.out.println("   public key: " + publicKey);
				RSAPublicKey pk = publicKeyFromBytes(getByteArr(fileBytes, i + 32 + 2 + 128 + 2, publicKeyLen));

				// Decrypt the signature with the provided public key
				decryptedSignature = decrypt(getByteArr(fileBytes, i + 32 + 2, 128), pk);
				if (decryptedSignature == null) {
					System.out.println("INVALID: NOT AN RSA");
					notRsa.add(j);
					invalid = true;
				}
				if (decryptedSignature != null) {
					System.out.println("   decryptedSignature:" + bytesToString(decryptedSignature, 0, decryptedSignature.length));
				}
				// The below will be used to build up a complete transaction (minus signatures) later
				prevTxRefBytes = getByteArr(fileBytes, i, 32);
				prevTxOutputIndexBytes = getByteArr(fileBytes, i + 32, 2);
				publicKeyLenBytes = getByteArr(fileBytes, i + 32 + 2 + 128, 2);
				publicKeyBytes = getByteArr(fileBytes, i + 32 + 2 + 128 + 2, publicKeyLen);

				// Save the byte[]'s that well need to recreate the entire transaction later
				prevTxRefBytesList.add(prevTxRefBytes);
				prevTxOutputIndexBytesList.add(prevTxOutputIndexBytes);
				publicKeyLenBytesList.add(publicKeyLenBytes);
				publicKeyBytesList.add(publicKeyBytes);

				transactionSizeSoFar += prevTxRefBytes.length + prevTxOutputIndexBytes.length + publicKeyLenBytes.length + publicKeyBytes.length;

				i += 164; // increment to account for everything except variable public key length (final field)
				i += publicKeyLen;
				if (txToOutputList.containsKey(prevTxRef)) {
					List<OutputSpecifier> list = txToOutputList.get(prevTxRef);
					if (prevTxOutputIndex < list.size()) {
						OutputSpecifier op = list.get(prevTxOutputIndex);
						if (!op.used) {
							op.used = true;
							inputTotal += op.value;
							list.set(prevTxOutputIndex, op);
						} else {
							invalid = true;
						}

						String hashedPubKey = op.hashedPublicKey;

						if (!hashedPubKey.equals(bytesToString(dHash(publicKeyBytes), 0, 32))) {
							invalid = true;
						}
					} else {
						invalid = true;
					}

				} else {
					invalid = true;
				}
			}

			byte[] nOutputsBytes = getByteArr(fileBytes, i, 2);
			transactionSizeSoFar += nOutputsBytes.length;

			short nOutputs = bytesToShort(fileBytes, i);
			System.out.println("   nOutputs: " + nOutputs);
			i += 2;

			transactionSizeSoFar += nOutputs * 36; // At this point we will know exactly how large of an array to make for the entire transaction
			byte[] currentTransaction = new byte[transactionSizeSoFar];

			// Start building up the transaction without signatures
			System.arraycopy(nInputsBytes, 0, currentTransaction, 0, nInputsBytes.length); // only one instance of this
			int inputSpecifierOffset = 0;
			int size1 = 0;
			int size2 = 0;
			int size3 = 0;
			int size4 = 0;
			for (int l = 0; l < nInputs; l++) {
				size1 = prevTxRefBytesList.get(l).length;
				size2 = prevTxOutputIndexBytesList.get(l).length;
				size3 = publicKeyLenBytesList.get(l).length;
				size4 = publicKeyBytesList.get(l).length;

				System.arraycopy(prevTxRefBytesList.get(l), 0, currentTransaction, inputSpecifierOffset + nInputsBytes.length, size1);
				System.arraycopy(prevTxOutputIndexBytesList.get(l), 0, currentTransaction, inputSpecifierOffset + nInputsBytes.length + size1, size2);
				System.arraycopy(publicKeyLenBytesList.get(l), 0, currentTransaction, inputSpecifierOffset + nInputsBytes.length + size1 + size2, size3);
				System.arraycopy(publicKeyBytesList.get(l), 0, currentTransaction, inputSpecifierOffset + nInputsBytes.length + size1 + size2 + size3, size4);
				inputSpecifierOffset += size1 + size2 + size3 + size4;
			}
			System.arraycopy(nOutputsBytes, 0, currentTransaction, nInputsBytes.length + inputSpecifierOffset, nOutputsBytes.length); // only one instance of this

			// This array will hold the remainder of the transaction, #outputs field + all output specifiers
			byte[] outputSpecifiers = new byte[nOutputs * 36];
			// Process each output specifier
			byte[] valueBytes = null;
			byte[] dHashPublicKeyBytes = null;
			List<OutputSpecifier> list = new ArrayList<OutputSpecifier>();
			for (int k = 0; k < nOutputs; k++) {
				valueBytes = getByteArr(fileBytes, i, 4);
				dHashPublicKeyBytes = getByteArr(fileBytes, i + 4, 32);

				System.arraycopy(valueBytes, 0, outputSpecifiers, k * 36, valueBytes.length);
				System.arraycopy(dHashPublicKeyBytes, 0, outputSpecifiers, 4 + k * 36, dHashPublicKeyBytes.length);
				System.out.println("   TxOut");
				int outputVal = bytesToInt(fileBytes, i);
				System.out.println("      value: " + outputVal); // Change 40 to CONSTANT?
				String hashedPublicKey = bytesToString(fileBytes, i + 4, 32);
				System.out.println("      dHashPublicKey: " + hashedPublicKey); // Change 32 to CONSTANT?
				i += 36;
				list.add(new OutputSpecifier(outputVal, hashedPublicKey));
				outputTotal += outputVal;
			}

			System.arraycopy(outputSpecifiers, 0, currentTransaction, currentTransaction.length - outputSpecifiers.length, outputSpecifiers.length);

			//System.out.println("Text:" + bytesToString(currentTransaction, 0, currentTransaction.length));

			byte[] dHash = dHash(currentTransaction);
			System.out.println("dHash: " + bytesToString(dHash, 0, dHash.length));

			byte[] fullTxBytes = getByteArr(fileBytes, startIndex, i - startIndex);
			byte[] dHashFullTx = dHash(fullTxBytes);
			String fullTx = bytesToString(dHashFullTx, 0, 32);
			if (!txToOutputList.containsKey(fullTx)) {
				txToOutputList.put(fullTx, list);
				allTx.add(dHashFullTx);
			} else {
				invalid = true;
			}

			if (outputTotal > inputTotal) {
				invalid = true;
			}

			if (invalid) {
				invalidTx++;
			}
		}
		System.out.println("notRsa count:" + notRsa.size());
		System.out.println("invalidTx count:" + invalidTx);
		byte[] merkleRoot = merkleTree(allTx);
	}


	// This will decrypt the given byte array using the given RSAPublic key and output a byte array
	public static byte[] decrypt(byte[] text, RSAPublicKey key) {
		byte[] dectyptedText = null;
		try {
			// get an RSA cipher object and print the provider
			final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

			// decrypt the text using the given public key
			cipher.init(Cipher.DECRYPT_MODE, key);
			dectyptedText = cipher.doFinal(text);
		} catch (IllegalArgumentException ex) {
			return null; // not an RSA key
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		return dectyptedText;
	}

	//
	public static byte[] encrypt(String text, RSAPublicKey key) {
		byte[] cipherText = null;
		try {
			// get an RSA cipher object and print the provider
			final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			// encrypt the plain text using the public key
			cipher.init(Cipher.ENCRYPT_MODE, key);
			cipherText = cipher.doFinal(text.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cipherText;
	}

	// This method will take care of reading the genesis block
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
		String tx = bytesToString(transactionDHash, 0, transactionDHash.length);
		System.out.println("   Transaction: dHash (name) = " + tx);
		System.out.println("      nInputs: " + bytesToShort(fileBytes, 86));
		System.out.println("      nOutputs: " + bytesToShort(fileBytes, 88));
		System.out.println("      TxOut");
		int outputVal = bytesToInt(fileBytes, 90);
		System.out.println("         value: " + outputVal);
		String hashedPublicKey = bytesToString(fileBytes, 94, 32);
		System.out.println("         dHashPublicKey: " + hashedPublicKey);
		if (!txToOutputList.containsKey(tx)) {
			List<OutputSpecifier> list = new ArrayList<OutputSpecifier>();
			list.add(new OutputSpecifier(outputVal, hashedPublicKey));
			txToOutputList.put(tx, list);
		}
		System.out.println("      Packed bytes: " + bytesToString(fileBytes, 86, 40));
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

	// This method will return a sub-array of the given "arr", starting at index "start" for "len" length
	public static byte[] getByteArr(byte[] arr, int start, int len) {
		byte[] buffer = new byte[len];
		for (int i = 0; i < buffer.length; i ++) {
			buffer[i] = arr[start + i];
		}
		return buffer;
	}

	// This method will return the int that is represented in the given array at "start" index in little endian order
	public static int bytesToInt(byte[] arr, int start) {
		byte[] bytes = new byte[4];
		for (int i = 0; i < 4; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	// This method will return the short that is represented in the given array at "start" index in little endian order
	public static short bytesToShort(byte[] arr, int start) {
		byte[] bytes = new byte[2];
		for (int i = 0; i < 2; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort();
	}

	// This method will return the long that is represented in the given array at "start" index in little endian order
	public static long bytesToLong(byte[] arr, int start) {
		byte[] bytes = new byte[8];
		for (int i = 0; i < 8; i ++) {
			bytes[i] = arr[start + i];
		}
		return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
	}

	// This method will return the hex byte string from byte array "arr" starting at index "start" for "len" length
	public static String bytesToString(byte[] arr, int start, int len) {
		StringBuffer result = new StringBuffer();
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i ++) {
			bytes[i] = arr[start + i];
			result.append(String.format("%02x", bytes[i]));
		}
		return result.toString();
	}

	// This message will be output if incorrect arguments are provided
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

	//
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
