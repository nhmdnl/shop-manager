package com.archsolutions.shopmanager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.archsolutions.shopmanager.data.cloud.KeystoreTokenCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {

    private val cipher = KeystoreTokenCipher()
    private val plain = "device-token-abc123".toByteArray()

    @Test
    fun encryptThenDecryptGivesTheSameBytes() {
        assertArrayEquals(plain, cipher.decrypt(cipher.encrypt(plain)))
    }

    @Test
    fun twoEncryptionsOfOnePlaintextDiffer() {
        assertFalse(cipher.encrypt(plain).contentEquals(cipher.encrypt(plain)))
    }

    @Test
    fun aTamperedBlobGivesNull() {
        val blob = cipher.encrypt(plain)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(blob))
        assertNull(cipher.decrypt(ByteArray(5)))
    }
}
