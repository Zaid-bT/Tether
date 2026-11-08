package io.github.zaidbt.tether

import android.content.Context

sealed interface SaveResult {
    data object Saved : SaveResult
    data object NeedsCode : SaveResult
}

// The only place allowed to write rules; never call db.replaceRules from anywhere else
object RuleRepository {
    suspend fun save(context: Context, newRules: List<BlockRule>): SaveResult {
        val db = TetherDatabase.get(context)
        val old = db.blockRuleDao().getAll().associate { it.packageName to it.limitMinutes }
        val new = newRules.associate { it.packageName to it.limitMinutes }

        // Loosening = a rule was removed or its limit raised (0 is the strictest value)
        val loosening = old.any { (pkg, limit) ->
            val n = new[pkg]
            n == null || n > limit
        }
        if (loosening && PasscodeManager.isSet(context) && !PasscodeManager.isUnlocked()) {
            return SaveResult.NeedsCode
        }
        db.replaceRules(newRules)
        return SaveResult.Saved
    }
}