package io.github.zaidbt.tether

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Wrong(val attemptsLeft: Int) : VerifyResult
    data class LockedOut(val secondsLeft: Long) : VerifyResult
}

object PasscodeManager {
    private const val PREFS = "tether_lock"
    private const val ITERATIONS = 120_000
    private const val SESSION_MS = 5 * 60_000L // how long a correct code keeps editing unlocked
    private const val TRIES_PER_ROUND = 5

    // Unlock session lives in memory only, so a process restart relocks
    private var unlockedUntil = 0L // elapsedRealtime

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(c: Context) = prefs(c).contains("hash")
    fun isUnlocked() = SystemClock.elapsedRealtime() < unlockedUntil
    fun lock() { unlockedUntil = 0L }

    // Salted PBKDF2 hash of the code
    private fun hash(code: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    // Store a new code (only the salt and hash are kept)
    suspend fun setPasscode(c: Context, code: String) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .putInt("failed", 0)
            .remove("lockEl").remove("lockWall")
            .apply()
    }

    // Check a code; a correct one opens the unlock session
    suspend fun verify(c: Context, code: String): VerifyResult = withContext(Dispatchers.Default) {
        val left = lockoutSecondsLeft(c)
        if (left > 0) return@withContext VerifyResult.LockedOut(left)

        val p = prefs(c)
        val salt = Base64.decode(p.getString("salt", "")!!, Base64.NO_WRAP)
        val saved = Base64.decode(p.getString("hash", "")!!, Base64.NO_WRAP)

        if (MessageDigest.isEqual(saved, hash(code, salt))) {
            p.edit().putInt("failed", 0).apply()
            unlockedUntil = SystemClock.elapsedRealtime() + SESSION_MS
            return@withContext VerifyResult.Ok
        }

        // Wrong code: after every 5th miss, lock entry with a doubling delay (30s, 60s, 120s...)
        val failed = p.getInt("failed", 0) + 1
        val edit = p.edit().putInt("failed", failed)
        if (failed % TRIES_PER_ROUND == 0) {
            val delaySec = 30L shl (failed / TRIES_PER_ROUND - 1).coerceAtMost(7)
            edit.putLong("lockEl", SystemClock.elapsedRealtime() + delaySec * 1000)
                .putLong("lockWall", System.currentTimeMillis() + delaySec * 1000)
                .putInt("boot", bootCount(c))
            edit.apply()
            return@withContext VerifyResult.LockedOut(delaySec)
        }
        edit.apply()
        VerifyResult.Wrong(TRIES_PER_ROUND - failed % TRIES_PER_ROUND)
    }

    private fun bootCount(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    // Remaining lockout, using both elapsed time and wall clock so a reboot or a clock change alone can't skip it
    private fun lockoutSecondsLeft(c: Context): Long {
        val p = prefs(c)
        val sameBoot = p.getInt("boot", -1) == bootCount(c)
        val byElapsed = if (sameBoot) p.getLong("lockEl", 0) - SystemClock.elapsedRealtime() else 0L
        val byWall = p.getLong("lockWall", 0) - System.currentTimeMillis()
        val leftMs = maxOf(byElapsed, byWall)
        return if (leftMs > 0) (leftMs + 999) / 1000 else 0L
    }
}