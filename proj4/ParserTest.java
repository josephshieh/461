import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.test.SimpleTest;

import java.io.*;
import java.math.BigInteger;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;

/**
 * basic class for reading test.pem - the password is "secret"
 */
public class ParserTest extends SimpleTest {
   public static final String PUB_KEY_STRING  = "-----BEGIN RSA PUBLIC KEY-----\n" +
           "MIGJAoGBANu2X9ijlIhDbaua5+x9BK/vrbntU6HQc1lO1RRCpRfK9DWhkzzJwIAB\n" +
           "Bm1NEWpTN4DhSv04qcbMpSzqSDYMxz9/x3lg6zmhRWwq5T7qa1hXDOB6ffhFpxV0\n" +
           "k1X5J0FC/YiVPg+8SgwUy5G9K4t9iPLVedoPddbYy07wpDrnPH1hAgMBAAE=\n" +
           "-----END RSA PUBLIC KEY-----\n";

   public String getName() {
      return "PEMParserTest";
   }

   private PEMParser openPEMResource(String fileName) throws FileNotFoundException {
      Reader fRd = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(PUB_KEY_STRING.getBytes())));
      return new PEMParser(fRd);
   }

   public void performTest() throws Exception {
      Object o;
      PEMParser pemRd = openPEMResource("461key.pem");
      while ((o = pemRd.readObject()) != null) {
         if (o instanceof SubjectPublicKeyInfo) {
            JcaPEMKeyConverter myConverter = new JcaPEMKeyConverter();
            RSAPublicKey myKey = (RSAPublicKey) myConverter.getPublicKey((SubjectPublicKeyInfo) o);
            BigInteger exponent = myKey.getPublicExponent();
            BigInteger modulus = myKey.getModulus();
            System.out.println("Exponent:");
            System.out.println(exponent);
            System.out.println("Modulus:");
            System.out.println(modulus);
         } else {
            System.out.println("Not an instance of SubjectPublicKeyInfo.");
         }
      }
   }

   public static void main(String[] args) {
      Security.addProvider(new BouncyCastleProvider());
      runTest(new ParserTest());
   }
}