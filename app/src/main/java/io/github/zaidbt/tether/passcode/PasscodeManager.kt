//This file is the lock. It stores the code safely, checks attempts, limits guessing, keeps the 5-minute unlock session, and runs the forgot-code timer.

package io.github.zaidbt.tether.passcode

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
    data class LockedOut(val secondsLeft: Long) : VerifyResult //seconds to wait
}

object PasscodeManager {
    private const val PREFS = "tether_lock" //storage file(SharedPreference), holds hash, salt and counters
    private const val ITERATIONS = 120_000 //hashing algo repeat
    private const val SESSION_MS = 5 * 60_000L // how long a correct code keeps editing unlocked (5 mins here)
    private const val TRIES_PER_ROUND = 3
    private const val RESET_MS = 30_000L //12 * 60 * 60_000L // forgot-code wait (use 30_000L only for testing)

    // Unlock session lives in memory only, so a process restart relocks
    private var unlockedUntil = 0L // elapsedRealtime(time since boot)...cant be changed by editing the clock

    //getter for storage file, MODE_PRIVATE: only tether can read it
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(c: Context) = prefs(c).contains("hash")  //code exists if hash entry is saved
    //called by MainActivity (line 248), RuleRepository (line 23), TetherAccessibilityService (line 61)

    fun isUnlocked() = SystemClock.elapsedRealtime() < unlockedUntil  //checks against (updated) unlockedUntil (line 69 updates its value)
    //called by RuleRepository (line 23), TetherAccessibilityService (line 61)

    fun lock() { unlockedUntil = 0L }  //called by MainActivity (line 157)

    // Salted PBKDF2 hash of the code
    private fun hash(code: String, salt: ByteArray): ByteArray { //salt is random data mixed in to make each hash unique
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded //encoded returns raw bytes
        //PBKDF2WithHmacSHA256 is a standard algorithm designed to be slow on purpose, which makes brute-forcing codes expensive
    }

    // Store a new code (only the salt and hash are kept) and clear any pending reset
    suspend fun setPasscode(c: Context, code: String) = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) } //16 byte random salt
        prefs(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP)) //Base64 encodes bytes to string
            .putString("hash", Base64.encodeToString(hash(code, salt), Base64.NO_WRAP))
            .putInt("failed", 0)
            .remove("lockEl").remove("lockWall") //clear active failed attempts lockout timers (elapsed uptime and wall clock)
            .remove("resetEl").remove("resetBoot") //clear any pending forgot passcode reset timers and boot trackers
            .apply()  //saves asynchronously, updates the in-memory values right away
        ResetNotifier.cancel(c) //removes countdown notification
    }//called by PasscodeUi (line 62)

    // Check a code; a correct one opens the unlock session and cancels any pending reset
    suspend fun verify(c: Context, code: String): VerifyResult = withContext(Dispatchers.Default) {
        val left = lockoutSecondsLeft(c)
        if (left > 0) return@withContext VerifyResult.LockedOut(left) //if lockout is still running, refuse service

        //load stored hash and salt
        val p = prefs(c)
        val salt = Base64.decode(p.getString("salt", "")!!, Base64.NO_WRAP)  //!! = not null
        val saved = Base64.decode(p.getString("hash", "")!!, Base64.NO_WRAP)

        if (MessageDigest.isEqual(saved, hash(code, salt))) { //MessageDigest.isEqual() takes same time regardless of input so timing cant be matched to number of bytes matched
            //on success; clear fail counter, open 5 min window session, cancel any pending forgot code reset
            p.edit().putInt("failed", 0).apply()
            unlockedUntil = SystemClock.elapsedRealtime() + SESSION_MS //grants 5 min unlock session
            cancelReset(c)
            return@withContext VerifyResult.Ok
        }

        // Wrong code: after every 5th miss, lock entry with a doubling delay (30s, 60s, 120s...)
        val failed = p.getInt("failed", 0) + 1
        val edit = p.edit().putInt("failed", failed)
        if (failed % TRIES_PER_ROUND == 0) {
            val delaySec = 30L shl (failed / TRIES_PER_ROUND - 1).coerceAtMost(7)
            //lockout on both clocks to avoid any loopholes (reboot, editing phone time)
            edit.putLong("lockEl", SystemClock.elapsedRealtime() + delaySec * 1000)
                .putLong("lockWall", System.currentTimeMillis() + delaySec * 1000)
                .putInt("boot", bootCount(c))
            edit.apply()
            return@withContext VerifyResult.LockedOut(delaySec)
        }
        edit.apply()
        VerifyResult.Wrong(TRIES_PER_ROUND - failed % TRIES_PER_ROUND)
    }//called by PasscodeUi (line 97)

    // ---- Forgot-code reset ----

    // Start the wait (does nothing if one is already running)
    fun startReset(c: Context) {
        if (resetRemainingMs(c) != null) return
        prefs(c).edit()
            .putLong("resetEl", SystemClock.elapsedRealtime())
            .putInt("resetBoot", bootCount(c))
            .apply()
    }//called by PasscodeUi (line 169)

    //deletes reset counter and notification
    fun cancelReset(c: Context) {
        prefs(c).edit().remove("resetEl").remove("resetBoot").apply()
        ResetNotifier.cancel(c)
    }//called by PasscodeUi (line 176)

    // Time left on the wait, or null if no reset is pending
    fun resetRemainingMs(c: Context): Long? {
        val p = prefs(c)
        if (!p.contains("resetEl")) return null

        // A reboot restarts the wait: elapsed time can't be compared across boots
        if (p.getInt("resetBoot", -1) != bootCount(c)) {
            p.edit()
                .putLong("resetEl", SystemClock.elapsedRealtime())
                .putInt("resetBoot", bootCount(c))
                .apply()
        }
        val readyAt = p.getLong("resetEl", 0) + RESET_MS //when reset code timer ends
        return (readyAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }//called by PasscodeUi (line 119)

    // Set a new code, only once the wait is over
    suspend fun completeReset(c: Context, newCode: String): Boolean {
        val remaining = resetRemainingMs(c) ?: return false //?: return false: exits when there is no pending request
        if (remaining > 0) return false
        setPasscode(c, newCode)
        return true
    }//called by PasscodeUi (line 185)

    //number of boots
    private fun bootCount(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1) //-1 id fallback if the system doesnt provide it

    // Remaining attempt lockout, using both elapsed time and wall clock so a reboot or a clock change alone can't skip it
    private fun lockoutSecondsLeft(c: Context): Long {
        val p = prefs(c)
        val sameBoot = p.getInt("boot", -1) == bootCount(c)
        val byElapsed = if (sameBoot) p.getLong("lockEl", 0) - SystemClock.elapsedRealtime() else 0L //only valid within same boot
        val byWall = p.getLong("lockWall", 0) - System.currentTimeMillis()
        val leftMs = maxOf(byElapsed, byWall) //taking max ensures changing clock/rebooting cant decrease the lockout
        return if (leftMs > 0) (leftMs + 999) / 1000 else 0L //round up
    }
}