//only code allowed to save rules, every save goes through it, "gate at the action, not the screen"

package io.github.zaidbt.tether.data

import android.content.Context
import io.github.zaidbt.tether.passcode.PasscodeManager

sealed interface SaveResult {  //sealed interface: closed set of allowed implementations known at compile time (enables exhaustive `when` checks).
    data object Saved : SaveResult
    data object NeedsCode : SaveResult  //ask for the code and call again
}

// The only place allowed to write rules; never call db.replaceRules from anywhere else
object RuleRepository {
    suspend fun save(context: Context, newRules: List<BlockRule>): SaveResult {  //suspend as it reads and writes database
        val db = TetherDatabase.get(context)  //gets the shared database
        //associate maps packageName to limitMinutes
        val old = db.blockRuleDao().getAll().associate { it.packageName to it.limitMinutes }  //returns a list of existing block rules
        val new = newRules.associate { it.packageName to it.limitMinutes }  //new rules user wants to add and maps it

        // Loosening = a rule was removed or its limit raised (0 is the strictest value)
        val loosening = old.any { (pkg, limit) ->  //loops over old rules and checks if any of them is wekaer/raised...becomes true if yes
            val n = new[pkg]
            n == null || n > limit  //null: rule removed
        }
        //require passcode only if loosening restrictions, a passcode exists, and the session isn't already unlocked
        if (loosening && PasscodeManager.isSet(context) && !PasscodeManager.isUnlocked()) {
            return SaveResult.NeedsCode
        }
        db.replaceRules(newRules)  //otherwise just write the new list
        return SaveResult.Saved
    }
}