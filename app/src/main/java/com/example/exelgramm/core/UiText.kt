package com.example.exelgramm.core

import android.content.Context
import androidx.annotation.StringRes

/**
 * Text that may be a string resource or a dynamic string.
 * Lets ViewModels build errors without a Context.
 */
sealed interface UiText {
    /** String resource with optional format args. */
    class StringResource(
        @param:StringRes val resId: Int,
        vararg val args: Any,
    ) : UiText {
        override fun equals(other: Any?): Boolean =
            other is StringResource && resId == other.resId && args.contentEquals(other.args)

        override fun hashCode(): Int = 31 * resId + args.contentHashCode()
        override fun toString(): String = "StringResource(resId=$resId)"
    }

    data class Dynamic(val value: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is StringResource -> context.getString(resId, *args)
        is Dynamic -> value
    }
}
