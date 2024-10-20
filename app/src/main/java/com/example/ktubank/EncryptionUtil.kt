package com.example.ktubank

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptionUtil {

    private val keyAlias =
        "KTUBankEncryptionKey" // This alias will be used to refer to the key in Keystore
    private val keystore = "AndroidKeyStore"
    private val gcmTagLength = 128 // GCM tag length in bits
    private val gcmNonceLength = 12 // GCM recommended nonce length (12 bytes)

    init {
        createKeyIfNeeded() // Ensure key is generated and stored in Keystore
    }

    // Function to generate a new encryption key and store it in the Keystore
    private fun createKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(keystore)
        keyStore.load(null)

        // Check if the key already exists in the Keystore
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystore)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(128) // AES-128
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey() // Generates and stores the key in the Keystore
        }
    }

    // Function to retrieve the encryption key from the Keystore
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(keystore)
        keyStore.load(null)
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    // Encrypts a given string using the secret key from Keystore
    fun encrypt(plainText: String): String {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        // No need to provide a nonce; the system will generate one for us
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        // Retrieve the generated IV (nonce) from the cipher
        val iv = cipher.iv

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine nonce (IV) and cipher text into a single byte array
        val encryptedBytes = iv + cipherText

        // Return the encrypted data as a Base64-encoded string
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    // Decrypts a Base64-encoded string using the secret key from Keystore
    fun decrypt(encryptedText: String): String {
        val secretKey = getSecretKey()
        val encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT)

        // Extract the IV (nonce) from the beginning of the encrypted data
        val iv = encryptedBytes.copyOfRange(0, gcmNonceLength)
        val cipherText = encryptedBytes.copyOfRange(gcmNonceLength, encryptedBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmParameterSpec = GCMParameterSpec(gcmTagLength, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}