package org.mifos.phee.kafkastreamer.importer.utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;

public class AesUtil {

    /**
     * Encrypts the string data using AES algorithm
     *
     * @param plaintext
     *            the string data to be encrypted
     * @param stringKey
     *            key in the Base64 string encoded format
     * @return ecnypted data
     * @throws Exception
     */
    public static String encrypt(String plaintext, String stringKey) throws Exception {
        SecretKey key = getSecretKey(stringKey);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return base64Encode(cipher.doFinal(plaintext.getBytes()));
    }

    /**
     * Decrypts the data using AES algorithm
     *
     * @param encryptedString
     *            Base64 encoded encrypted string
     * @param stringKey
     *            key in the Base64 string encoded format
     * @return decrypted data in string format
     * @throws Exception
     */
    public static String decrypt(String encryptedString, String stringKey) throws Exception {
        SecretKey key = getSecretKey(stringKey);
        byte[] cipherText = base64Decode(encryptedString);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return new String(cipher.doFinal(cipherText));
    }

    // encodes the given byte array to Base64
    public static String base64Encode(byte[] data) {
        return Base64.encodeBase64String(data);
    }

    // decodes the base64 encoded String to byte array
    public static byte[] base64Decode(String base64EncodedString) {
        return Base64.decodeBase64(base64EncodedString);
    }

    // get instance of class [SecretKey] using the string format of the key
    public static SecretKey getSecretKey(String key) {
        byte[] aesByte = base64Decode(key);
        return new SecretKeySpec(aesByte, "AES");
    }

    // generates and returns the string encoded AES key
    public static String generateSecretKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, new SecureRandom());
        SecretKey key = keyGenerator.generateKey();
        return base64Encode(key.getEncoded());
    }
    public static boolean checkForMaskingFields(JSONObject jsonObject, List<String> fieldsRequiredMasking) {
        for (String field : fieldsRequiredMasking) {
            if (!jsonObject.has(field)) {
                return true;
            }
        }
        return false;
    }

}
