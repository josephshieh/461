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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.Timestamp;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Cipher;

/**
 *  Joseph Shieh, Sergey Naumets
 *
 */
public class BitCoins {
	static Map<String, List<OutputSpecifier>> txToOutputList;
	static Map<String, Integer> balances;

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
		balances = new HashMap<String, Integer>();

		// Read Genesis Block info
		readGenesisBlock(fileBytes);
		System.out.println();
		
		//write(getByteArr(fileBytes, 0, 126), outFilename);

		// Transaction Count of the rest
		int transactionCount = bytesToInt(fileBytes, 126);
		System.out.println("Transaction count: " + transactionCount);
		int tips = 0;
		int invalidTx = 0;
		List<byte[]> allTx = new ArrayList<byte[]>(); // dHashed
		List<byte[]> allFullTxBytes = new ArrayList<byte[]>();
		long allTxFullLength = 0;
		i = 130; // Genesis stuff plus transaction count processed
		// First transaction at index 130
		List<Integer> notRsa = new ArrayList<Integer>();
		for (int j = 0; j < transactionCount; j++) {
			Map<String, Integer> tempBalances = new HashMap<String, Integer>(balances);
			int startIndex = i;
			boolean invalid = false;
			int inputTotal = 0;
			int outputTotal = 0;
			int curTip = 0;
			System.out.println("Transaction # " + j + " ---------------------------------------------------------");
			short nInputs = bytesToShort(fileBytes, i);
			System.out.println("   nInputs: " + nInputs);
			byte[] nInputsBytes = getByteArr(fileBytes, i, 2);
			i += 2;
			String curHashedPubKey = null;
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
						String hashedPubKey = op.hashedPublicKey;
						curHashedPubKey = bytesToString(dHash(publicKeyBytes), 0, 32);
						if (!hashedPubKey.equals(curHashedPubKey)) {
							invalid = true;
						}
						if (!op.used) {
							op.used = true;
							inputTotal += op.value;
							list.set(prevTxOutputIndex, op);
							// Remove balance from sender
							int oldGiver = tempBalances.get(hashedPubKey);
							tempBalances.put(hashedPubKey, oldGiver - op.value);
						} else {
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
				// Add values to receiver
				if (tempBalances.containsKey(hashedPublicKey)) {
					int oldReceiver = tempBalances.get(hashedPublicKey);
					tempBalances.put(hashedPublicKey, oldReceiver + outputVal);
				} else {
					tempBalances.put(hashedPublicKey, outputVal);
				}
			}

			System.arraycopy(outputSpecifiers, 0, currentTransaction, currentTransaction.length - outputSpecifiers.length, outputSpecifiers.length);

			//System.out.println("Text:" + bytesToString(currentTransaction, 0, currentTransaction.length));

			byte[] dHash = dHash(currentTransaction);
			System.out.println("dHash: " + bytesToString(dHash, 0, dHash.length));

			byte[] fullTxBytes = getByteArr(fileBytes, startIndex, i - startIndex);
			byte[] dHashFullTx = dHash(fullTxBytes);
			String fullTx = bytesToString(dHashFullTx, 0, 32);

			System.out.println("full dHash: " + fullTx);
			if (!txToOutputList.containsKey(fullTx)) {
				invalid = true;
			}


			if (outputTotal > inputTotal) {
				invalid = true;
			} else {
				curTip = inputTotal - outputTotal;
				tips += curTip;
			}

			if (invalid) {
				invalidTx++;
			} else {
				if (!txToOutputList.containsKey(fullTx)) {
					txToOutputList.put(fullTx, list);
					allTx.add(dHashFullTx);
					allFullTxBytes.add(fullTxBytes);
					allTxFullLength += fullTxBytes.length;
				}
				tempBalances.put(curHashedPubKey, tempBalances.get(curHashedPubKey) + curTip);
				balances = new HashMap<String, Integer>(tempBalances);
			}

		}
		System.out.println("invalidTx count:" + invalidTx);
		
		// Write new file
		byte[] output = new byte[126 + 82 + 4 + 40 + (int) allTxFullLength];
		byte[] genesisAll = getByteArr(fileBytes, 0, 126);
		System.arraycopy(genesisAll, 0, output, 0, genesisAll.length); // write to final output array: Genesis header, trans count, transaction
		
		byte[] genesisBlockHeader = getByteArr(fileBytes, 0, 82);
		byte[] prevBlockRef = dHash(genesisBlockHeader); // 32 bytes
		
		byte[] blockOneHeader = new byte[82]; //getByteArr(fileBytes, 0, 82); // can keep first 4 bytes, version
		
		byte[] version = intToBytes(1);
		System.arraycopy(version, 0, blockOneHeader, 0, version.length);
		
		System.arraycopy(prevBlockRef, 0, blockOneHeader, 4, prevBlockRef.length); // next 32 bytes
		
		
		SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date now = new Date();
		String strDate = sdfDate.format(now);
		//System.out.println(strDate);
		
		int yr = Integer.parseInt(strDate.split(" ")[0].split("-")[0]);
		int mo = Integer.parseInt(strDate.split(" ")[0].split("-")[1]) - 1; // month is 0-based index?
		int da = Integer.parseInt(strDate.split(" ")[0].split("-")[2]);
		int hr = Integer.parseInt(strDate.split(" ")[1].split(":")[0]);
		int mi = Integer.parseInt(strDate.split(" ")[1].split(":")[1]);
		int se = Integer.parseInt(strDate.split(" ")[1].split(":")[2]);
		//System.out.println(yr + "-" + mo + "-" + da + " " + hr + ":" + mi+ ":" + se );
		
		Calendar cal = Calendar.getInstance();
		cal.clear();
		cal.set(yr, mo, da, hr, mi, se); // month is 0 based index 2 = Calendar.MARCH
		//String dateSample = "2014-03-04 18:48:23";
	    //cal.set(2014, 2, 4, 18, 48, 23); // month is 0 based index 2 = Calendar.MARCH
		int epochTime = (int) (cal.getTimeInMillis() / 1000L);
	    System.out.println(epochTime);
		byte[] creationTime = intToBytes(epochTime);
		System.arraycopy(creationTime, 0, blockOneHeader, 4 + 32 + 32, creationTime.length); // next 32 bytes
		
		short difficulty = 3;
		byte[] diff = shortToBytes(difficulty);
		System.arraycopy(diff, 0, blockOneHeader, 4 + 32 + 32 + 4, diff.length); // next 32 bytes
		
		byte[] blockOneTransactionCount = intToBytes(transactionCount - invalidTx + 1); // plus coinbase
		System.arraycopy(blockOneTransactionCount, 0, output, 126 + 82, blockOneTransactionCount.length); // write to final output array
		
		byte[] coinbaseTransaction = new byte[40];
		
		byte[] nInputs = shortToBytes((short) 0);
		System.arraycopy(nInputs, 0, output, 126 + 82 + 4, nInputs.length);
		System.arraycopy(nInputs, 0, coinbaseTransaction, 0, nInputs.length);
		
		byte[] nOutputs = shortToBytes((short) 1);
		System.arraycopy(nOutputs, 0, output, 126 + 82 + 4 + 2, nOutputs.length);
		System.arraycopy(nOutputs, 0, coinbaseTransaction, 2, nOutputs.length);
		
		byte[] value = intToBytes(10 /*+ tips*/);
		System.arraycopy(value, 0, output, 126 + 82 + 4 + 2 + 2, value.length);
		System.arraycopy(value, 0, coinbaseTransaction, 2 + 2, value.length);
		
		byte[] hashedPublicKey = hexStringToBytes("1f5a0200bc94ae4264642855786d9c2bb436b9e129ef95e6416136c03f339581"); //dHash of our public key, given in spec
		System.arraycopy(hashedPublicKey, 0, output, 126 + 82 + 4 + 2 + 2 + 4, hashedPublicKey.length);
		System.arraycopy(hashedPublicKey, 0, coinbaseTransaction, 2 + 2 + 4, hashedPublicKey.length);
		
		byte[] dHashCoinbase = dHash(coinbaseTransaction);
		allTx.add(0, dHashCoinbase);
		System.out.println("size:" + allTx.size());

		System.out.println("tips count:" + tips);
		int totalBitcoins = 0;
		System.out.println("Balances: ");
		for (String hashedPubKey: balances.keySet()) {
			int coins = balances.get(hashedPubKey);
			System.out.println("Hashed Public Key " + hashedPubKey + ": " + coins);
			totalBitcoins += coins;
		}
		System.out.println("Total number of bitcoins: " + totalBitcoins);

		byte[] merkleRoot = merkleTree(allTx);
		
		System.out.println("Merk:" + bytesToString(merkleRoot, 0, 32));
		System.arraycopy(merkleRoot, 0, blockOneHeader, 4 + 32, merkleRoot.length); // next 32 bytes
		
		System.out.println("block:" + bytesToString(blockOneHeader, 0, blockOneHeader.length));
		
		byte[] nonce = hexStringToBytes("ccd17b0000000000");
		System.arraycopy(nonce, 0, blockOneHeader, 74, nonce.length);
		/*byte[] nonce = null;
		long counter = 0; // - Math.pow(2, 63)
		while (!bytesToString(dHash(blockOneHeader), 0, 32).substring(0, 6).equals("000000")) {
			nonce = longToBytes(counter);
			System.arraycopy(nonce, 0, blockOneHeader, 74, nonce.length);
			counter++;
		}
		
		System.out.println("Nonce:" + bytesToString(nonce, 0, 8));
		System.out.println("Long val of nonce:" + bytesToLong(nonce, 0));*/
		
		System.arraycopy(blockOneHeader, 0, output, 126, blockOneHeader.length); // write to final output array
		
		int currentOffset = 0;
		for (int l = 0; l < allFullTxBytes.size(); l++) {
			byte[] bytes = allFullTxBytes.get(l);
			System.arraycopy(bytes, 0, output, 126 + 82 + 4 + 40 + currentOffset, bytes.length);
			currentOffset += bytes.length;
		}
		
		/*write(getByteArr(fileBytes, 0, 126), outFilename);*/
		write(output, outFilename);
		
		
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
		} catch (Exception ex) {
			return null;
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
		balances.put(hashedPublicKey, outputVal);
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
	
	// This method will return a byte array representing the long in little endian order
	public static byte[] longToBytes(long num) {
		return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(num).array();
	}
	
	// This method will return a byte array representing the int in little endian order
	public static byte[] intToBytes(int num) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(num).array();
	}
	
	// This method will return a byte array representing the short in little endian order
	public static byte[] shortToBytes(short num) {
		return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(num).array();
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
	
	// This method will return the hex byte string from byte array "arr" starting at index "start" for "len" length
		public static byte[] hexStringToBytes(String str) {
			byte[] bytes = new byte[str.length() / 2];
			for (int i = 0; i < str.length(); i += 2) {
		        bytes[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
		                             + Character.digit(str.charAt(i+1), 16));
		    }
			return bytes;
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
	public static void write(byte[] aInput, String aOutputFileName){
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
