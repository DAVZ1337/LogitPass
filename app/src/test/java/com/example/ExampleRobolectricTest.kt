package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.CryptoManager
import com.example.security.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LogitPass", appName)
  }

  @Test
  fun `password generator produces correct length and strong entropy`() {
    val config = PasswordGenerator.GeneratorConfig(length = 24)
    val password = PasswordGenerator.generatePassword(config)
    assertEquals(24, password.length)

    val strength = PasswordGenerator.evaluateStrength(password)
    assertTrue(strength.entropyBits > 60)
  }

  @Test
  fun `crypto manager performs authenticated AES-256-GCM encryption and decryption`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = CryptoManager(context)

    val setupOk = crypto.setupVault("TestVault", "SuperMasterKey123!", false)
    assertTrue(setupOk)
    assertTrue(crypto.isUnlocked)

    val secret = "SecretCredentialPassword_2026!#"
    val encrypted = crypto.encryptString(secret)
    assertTrue(encrypted.isNotEmpty())
    assertTrue(encrypted != secret)

    val decrypted = crypto.decryptString(encrypted)
    assertEquals(secret, decrypted)

    crypto.lockVault()
    assertTrue(!crypto.isUnlocked)

    val unlockOk = crypto.unlockWithPassword("SuperMasterKey123!")
    assertTrue(unlockOk)
    assertTrue(crypto.isUnlocked)
  }
}
