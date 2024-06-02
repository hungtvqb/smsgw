package com.smssender;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

public class TextSecurity
{

    // This array is used to convert from bytes to hexadecimal numbers
    static private final char[] digits =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private String share_key=null;
    public enum CheckSumAlgorithm
    {
        Adler32, CRC32
    }

    public enum MessageDigestAlgorithm
    {
        MD5, SHA
    }

    /**
     * based on sun link
     * https://java.sun.com/j2se/1.4.2/docs/guide/security/jce/JCERefGuide.html#AppA
     * DESede is Tripple DES algorithm
     *
     * @author xakn1
     *
     */
    public enum EncryptionAlgorithm
    {
        DES, SHA, DESede, RC2, RC4, RC5
    }

    public enum EncryptionMode
    {
        NONE, CBC, CFB, ECB, OFB, PCBC
    }

    public enum EncryptionPadding {

        NoPadding, PKCS5Padding, SSL3Padding
    }

    public void setShareKey(String str)
    {
        share_key=str;
    }
    /**
     *
     * @param input
     * @param alogorithm
     *            e.g MD5, SHA algorithms
     */
    public void computeDigest(String input, MessageDigestAlgorithm alogorithm)
    {

        // Generate Des key and vector using MD5, SHA
        MessageDigest msgdig;
        try {
            msgdig = MessageDigest.getInstance(alogorithm.name());
        } catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }

        msgdig.update(input.getBytes());
        byte[] mdbytes = msgdig.digest();
    }

    /**
     *
     * @param input
     * @param algorthm
     * @return
     */
    public long computeCheckSum(String input, CheckSumAlgorithm algorithm) {
        long sum = -1;

        Checksum cs = null;

        if (algorithm.equals(CheckSumAlgorithm.Adler32)) {
            cs = new Adler32();
        } else if (algorithm.equals(CheckSumAlgorithm.CRC32)) {
            cs = new CRC32();
        }

        cs.update(input.getBytes(), 0, input.length());
        sum = cs.getValue();
        return sum;
    }

    /**
     * hexEncode is create String object which contains hex representation of
     * data present in bytes array
     *
     * @param bytes -
     *            input array of bytes to be hex coded
     * @return String objecting containing hex representation of bytes in input
     *         array
     */
    public String hexEncode(byte[] bytes) {
        StringBuilder buffer = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            byte byteValue = bytes[i];
            buffer.append(digits[(byteValue & 0xf0) >> 4]);
            buffer.append(digits[byteValue & 0x0f]);
        }
        return buffer.toString();
    }

    public byte[] hexStringToByteArray(String s)
    {
        if(s==null || s.isEmpty() == true) return null;
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     *
     * @param clearText
     * @param cipher
     * @param secretKey
     * @param ivVector
     *            Initialization Vector
     * @return
     * @throws PAPException
     */
    public byte[] encrypt(String clearText, Cipher cipher, SecretKey secretKey, IvParameterSpec ivVector) {

        byte[] ciphertext = null;
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivVector);
            ciphertext = cipher.doFinal(clearText.getBytes());
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return ciphertext;
    }

    /**
     *
     * @param cipherText
     * @param cipher
     * @param secretKey
     * @param ivVector
     * @return
     * @throws PAPException
     */
    public String decrypt(byte[] cipherText, Cipher cipher, SecretKey secretKey, IvParameterSpec ivVector) {
        byte[] cleartext = null;
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivVector);
            cleartext = cipher.doFinal(cipherText);
        } catch (Exception e) {
//            System.out.println("Exception while decrypting data:" + hexEncode(cipherText) + " Exception:" + e);
            throw new RuntimeException(e);
        }

//        System.out.println("Decrypt -> Data - " + hexEncode(cipherText) + " is encoded as --> " + new String(cleartext));

        return new String(cleartext);
    }

//    public byte[] encryptPass(String pass) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException
//    {
//        computeDigest("SecretWord", MessageDigestAlgorithm.MD5);
//        computeDigest("SecretWord", MessageDigestAlgorithm.SHA);
//        computeCheckSum("SecretWord", CheckSumAlgorithm.Adler32);
//        computeCheckSum("SecretWord", CheckSumAlgorithm.CRC32);
//        //-------------- Testing encryption & decryption --------------
//        String input = "Testing encryption1";
//        //uses the first 24 bytes in the secret
//        //        DESedeKeySpec keyspec = new DESedeKeySpec(secret.getBytes());
//        DESedeKeySpec keyspec = new DESedeKeySpec("vasvasvas.viettelviettelviettel.pthtpthtptht.MT".getBytes());
//        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(EncryptionAlgorithm.DESede.name());
//        SecretKey deskey = keyfactory.generateSecret(keyspec);
//
//        // Create an 8-byte initialization vector
//        byte[] iv = new byte[]{(byte) 0x8E, 0x12, 0x39, (byte) 0x9C, 0x07, 0x72, 0x6F, 0x5A};
//
//        IvParameterSpec ivVector = new IvParameterSpec(iv);
//        Cipher cipher = Cipher.getInstance(EncryptionAlgorithm.DESede.name() + "/" + EncryptionMode.CBC.name() + "/"
//                + EncryptionPadding.PKCS5Padding.name());
////        byte[] cipherText = encrypt(input, cipher, deskey, ivVector);
////        System.out.println("Encrypted Text = " + hexEncode(cipherText));
////        String clearText = decrypt(cipherText, cipher, deskey, ivVector);
////        System.out.println("Clear Text = " + clearText);
//        byte[] ciphertext = null;
//        try
//        {
//            cipher.init(Cipher.ENCRYPT_MODE, deskey, ivVector);
//            ciphertext = cipher.doFinal(pass.getBytes());
//        } catch (Exception e)
//        {
//            throw new RuntimeException(e);
//        }
//        return ciphertext;
//    }

    public byte[] encryptPass2(String pass) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException
    {
        computeDigest("SecretWord", MessageDigestAlgorithm.MD5);
        computeDigest("SecretWord", MessageDigestAlgorithm.SHA);
        computeCheckSum("SecretWord", CheckSumAlgorithm.Adler32);
        computeCheckSum("SecretWord", CheckSumAlgorithm.CRC32);
        //uses the first 24 bytes in the secret
        //        DESedeKeySpec keyspec = new DESedeKeySpec(secret.getBytes());
        DESedeKeySpec keyspec = 
                new DESedeKeySpec(share_key.getBytes());
        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(EncryptionAlgorithm.DESede.name());
        SecretKey deskey = keyfactory.generateSecret(keyspec);

        // Create an 8-byte initialization vector
        byte[] iv = new byte[]{(byte) 0x8E, 0x12, 0x39, (byte) 0x9C, 0x07, 0x72, 0x6F, 0x5A};

        IvParameterSpec ivVector = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(EncryptionAlgorithm.DESede.name() + "/" + EncryptionMode.CBC.name() + "/"
                + EncryptionPadding.PKCS5Padding.name());
        byte[] ciphertext = null;
        try
        {
            cipher.init(Cipher.ENCRYPT_MODE, deskey, ivVector);
            ciphertext = cipher.doFinal(pass.getBytes());
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return ciphertext;
    }

//    public String decryptPass(byte[] input) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, UnsupportedEncodingException
//    {
//        if(input == null || input.length ==0) return null;
//        computeDigest("SecretWord", MessageDigestAlgorithm.MD5);
//        computeDigest("SecretWord", MessageDigestAlgorithm.SHA);
//
//        computeCheckSum("SecretWord", CheckSumAlgorithm.Adler32);
//        computeCheckSum("SecretWord", CheckSumAlgorithm.CRC32);
//
//        //-------------- Testing encryption & decryption --------------
//
//        //uses the first 24 bytes in the secret
//
//        //        DESedeKeySpec keyspec = new DESedeKeySpec(secret.getBytes());
//        DESedeKeySpec keyspec = new DESedeKeySpec("vasvasvas.viettelviettelviettel.pthtpthtptht.MT".getBytes());
//        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(EncryptionAlgorithm.DESede.name());
//        SecretKey deskey = keyfactory.generateSecret(keyspec);
//        // Create an 8-byte initialization vector
//        byte[] iv = new byte[]{(byte) 0x8E, 0x12, 0x39, (byte) 0x9C, 0x07, 0x72, 0x6F, 0x5A};
//        IvParameterSpec ivVector = new IvParameterSpec(iv);
//        Cipher cipher = Cipher.getInstance(EncryptionAlgorithm.DESede.name() + "/" + EncryptionMode.CBC.name() + "/"
//                + EncryptionPadding.PKCS5Padding.name());
//
////        String clearText = decrypt(cipherText, cipher, deskey, ivVector);
////
////        System.out.println("Clear Text = " + clearText);
//        byte[] cleartext = null;
//        try {
//            cipher.init(Cipher.DECRYPT_MODE, deskey, ivVector);
//            cleartext = cipher.doFinal(input);
//        } catch (Exception e) {
//            System.out.println("Exception while decrypting data:" + hexEncode(input) + " Exception:" + e);
//            throw new RuntimeException(e);
//        }
////        System.out.println("Decrypt -> Data - " + hexEncode(cipherText) + " is encoded as --> " + new String(cleartext));
//        return new String(cleartext);
//    }

    public String decryptPass2(byte[] input) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, UnsupportedEncodingException
    {
        if(input == null || input.length ==0) return null;
        computeDigest("SecretWord", MessageDigestAlgorithm.MD5);
        computeDigest("SecretWord", MessageDigestAlgorithm.SHA);

        computeCheckSum("SecretWord", CheckSumAlgorithm.Adler32);
        computeCheckSum("SecretWord", CheckSumAlgorithm.CRC32);

        //-------------- Testing encryption & decryption --------------
        DESedeKeySpec keyspec = new DESedeKeySpec(share_key.getBytes());
        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(EncryptionAlgorithm.DESede.name());
        SecretKey deskey = keyfactory.generateSecret(keyspec);
        // Create an 8-byte initialization vector
        byte[] iv = new byte[]{(byte) 0x8E, 0x12, 0x39, (byte) 0x9C, 0x07, 0x72, 0x6F, 0x5A};
        IvParameterSpec ivVector = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(EncryptionAlgorithm.DESede.name() + "/" + EncryptionMode.CBC.name() + "/"
                + EncryptionPadding.PKCS5Padding.name());
        byte[] cleartext = null;
        try {
            cipher.init(Cipher.DECRYPT_MODE, deskey, ivVector);
            cleartext = cipher.doFinal(input);
        } catch (Exception e) {
            System.out.println("Exception while decrypting data:" + hexEncode(input) + " Exception:" + e);
            throw new RuntimeException(e);
        }
        return new String(cleartext);
    }
//    /**
//     * @param args
//     */
//    public static void main1(String[] args) throws Exception
//    {
//        TextSecurity sec= new TextSecurity();
//        sec.computeDigest("SecretWord", MessageDigestAlgorithm.MD5);
//        sec.computeDigest("SecretWord", MessageDigestAlgorithm.SHA);
//
//        sec.computeCheckSum("SecretWord", CheckSumAlgorithm.Adler32);
//        sec.computeCheckSum("SecretWord", CheckSumAlgorithm.CRC32);
//
//        //-------------- Testing encryption & decryption --------------
//
//        String input = "vcgwcheckbuss";
//
//
//        //uses the first 24 bytes in the secret
//
//        //        DESedeKeySpec keyspec = new DESedeKeySpec(secret.getBytes());
//        DESedeKeySpec keyspec = new DESedeKeySpec("vasvasvas.viettelviettelviettel.pthtpthtptht.MT".getBytes());
//        SecretKeyFactory keyfactory = SecretKeyFactory.getInstance(EncryptionAlgorithm.DESede.name());
//        SecretKey deskey = keyfactory.generateSecret(keyspec);
//
//        // Create an 8-byte initialization vector
//        byte[] iv = new byte[]{(byte) 0x8E, 0x12, 0x39, (byte) 0x9C, 0x07, 0x72, 0x6F, 0x5A};
//
//        IvParameterSpec ivVector = new IvParameterSpec(iv);
//        Cipher cipher = Cipher.getInstance(EncryptionAlgorithm.DESede.name() + "/" + EncryptionMode.CBC.name() + "/"
//                + EncryptionPadding.PKCS5Padding.name());
//
//
//        byte[] cipherText = sec.encrypt(input, cipher, deskey, ivVector);
//
//        System.out.print("cipherText:");
//         for(byte val:cipherText)
//        {
//            System.out.print(+val);
//        }
//         System.out.println("");
//        System.out.println("Encrypted Text = " + sec.hexEncode(cipherText));
//
//
//        String clearText = sec.decrypt(cipherText, cipher, deskey, ivVector);
//
//        System.out.println("Clear Text = " + clearText);
//
//        //
//
//        String str="vcgwcheckbuss";
////        allCommandId=(VPassRevertId)str.getBytes();
//        System.out.println("password for encrypt:"+str);
//        byte [] bt=sec.encryptPass(str);
//        System.out.println("array of byte encryptPass:"+bt);
//        System.out.println("array of byte to hex = " + sec.hexEncode(bt));
//        for(byte val:bt)
//        {
//            System.out.print(val);
//        }
//        System.out.println("\r\n"+"decryptPass:"+sec.decryptPass(bt));
//        byte [] hextobt=sec.hexStringToByteArray("827AD0F4DF2F9D120378DA604FE8CED6");
//        System.out.println("decryptPass hextobt:"+sec.decryptPass(hextobt));
//    }

    public String Encrypt(String str) {
        String result = null;
        try {
            result = hexEncode(encryptPass2(str));
        } catch (Exception ex) {
            System.out.println("Error while trying to Encrypt '" + str + "': " + ex.getMessage());
            result = null;
        }

        return result;
    }

    public String Decrypt(String str) {
        String result = null;
        try {
            result = decryptPass2(hexStringToByteArray(str));
        } catch (Exception ex) {
            System.out.println("Error while trying to Encrypt '" + str + "': " + ex.getMessage());
            result = null;
        }

        return result;
    }

    private TextSecurity() {

    }

    private static TextSecurity mMe = null;

    public static TextSecurity getInstance() {
        if (mMe ==null) {
            mMe = new TextSecurity();
            mMe.share_key="JKHFYI**(#&$jkskjdfowe;;924782po202348274029340JHUTk,.mn,.kns";
        }

        return mMe;
    }

    public void setKey(String key) {
        this.share_key = key;
    }
    
    public void setDefaultKey() {
        this.share_key = "JKHFYI**(#&$jkskjdfowe;;924782po202348274029340JHUTk,.mn,.kns";
    }
    public static void main(String[] args) throws Exception
    {
        TextSecurity sec = TextSecurity.getInstance();
        String str="ttcacheadm156";

        String en = sec.Encrypt(str);
        System.out.println("'" + str + "' -> '" + en + "'");

        String desc = sec.Decrypt(en);
        System.out.println("'" + en + "' -> '" + desc + "'");
    }
}
