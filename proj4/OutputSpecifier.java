
public class OutputSpecifier {
	int value;
	String hashedPublicKey;
	boolean used;

	public OutputSpecifier(int value, String hashedPublicKey) {
		this.value = value;
		this.hashedPublicKey = hashedPublicKey;
		this.used = false;
	}
}
