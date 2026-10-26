package com.ivy.ui.transactionlook

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ivy.base.legacy.SharedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Text size of the account and category tags on transaction cards. [Default] is the original look. */
enum class TagTextSize(val sp: Int) {
    Small(sp = 10),
    Default(sp = 12),
    Large(sp = 14),
    ExtraLarge(sp = 16),
}

/** How transaction cards look, set in Settings → Transaction look. */
@Immutable
data class TransactionLook(
    val accountTagSize: TagTextSize,
    val categoryTagSize: TagTextSize,
    /** The category tag on the left and the account tag on the right, instead of side by side. */
    val accountOnRight: Boolean,
) {
    companion object {
        val Default = TransactionLook(
            accountTagSize = TagTextSize.Default,
            categoryTagSize = TagTextSize.Default,
            accountOnRight = false,
        )
    }
}

/** Provided at the root of the app; screenshots and previews get the original look. */
@Suppress("CompositionLocalAllowlist")
val LocalTransactionLook = staticCompositionLocalOf { TransactionLook.Default }

@Singleton
class TransactionLookStore @Inject constructor(
    private val sharedPrefs: SharedPrefs,
) {
    private val state = MutableStateFlow(load())
    val look: StateFlow<TransactionLook> = state.asStateFlow()

    fun setAccountTagSize(size: TagTextSize) {
        sharedPrefs.putString(SharedPrefs.ACCOUNT_TAG_TEXT_SIZE, size.name)
        state.update { it.copy(accountTagSize = size) }
    }

    fun setCategoryTagSize(size: TagTextSize) {
        sharedPrefs.putString(SharedPrefs.CATEGORY_TAG_TEXT_SIZE, size.name)
        state.update { it.copy(categoryTagSize = size) }
    }

    fun setAccountOnRight(onRight: Boolean) {
        sharedPrefs.putBoolean(SharedPrefs.ACCOUNT_TAG_ON_RIGHT, onRight)
        state.update { it.copy(accountOnRight = onRight) }
    }

    private fun load(): TransactionLook = TransactionLook(
        accountTagSize = size(SharedPrefs.ACCOUNT_TAG_TEXT_SIZE),
        categoryTagSize = size(SharedPrefs.CATEGORY_TAG_TEXT_SIZE),
        accountOnRight = sharedPrefs.getBoolean(SharedPrefs.ACCOUNT_TAG_ON_RIGHT, false),
    )

    private fun size(key: String): TagTextSize =
        TagTextSize.entries.firstOrNull { it.name == sharedPrefs.getString(key, null) } ?: TagTextSize.Default
}
