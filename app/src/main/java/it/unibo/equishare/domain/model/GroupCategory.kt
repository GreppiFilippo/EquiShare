/** Defines the Group Category domain model. */
package it.unibo.equishare.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A category, both for groups (`group_categories` table) and for the activity
 * feed visualisation.
 *
 * Why an enum: previously the codebase had three near-identical `when` blocks
 * (`iconForCategoryKey`, `iconForGroupType`, `tintForGroupIconKey`) on the same
 * underlying string keys. By making each category own its icon and tint, those
 * three functions collapse into property lookups — closing the OCP gap and
 * removing duplication.
 *
 * The string [iconKey] mirrors the DB column so categories still round-trip
 * through Supabase (`group_categories.icon_key`).
 */
enum class GroupCategory(
    val iconKey: String,
    val icon: ImageVector,
    val tint: CategoryTint,
    val groupType: GroupType,
) {
    FLIGHT("flight",                Icons.Default.Flight,         CategoryTint.BLUE,   GroupType.TRIP),
    BEACH("beach_access",           Icons.Default.BeachAccess,    CategoryTint.BLUE,   GroupType.TRIP),
    HOME("home",                    Icons.Default.Home,           CategoryTint.GREEN,  GroupType.HOME),
    RESTAURANT("restaurant",        Icons.Default.Restaurant,     CategoryTint.ORANGE, GroupType.FRIENDS),
    GIFT("card_giftcard",           Icons.Default.CardGiftcard,   CategoryTint.PINK,   GroupType.COUPLE),
    CELEBRATION("celebration",      Icons.Default.Celebration,    CategoryTint.PINK,   GroupType.FRIENDS),
    MUSIC("music_note",             Icons.Default.MusicNote,      CategoryTint.PINK,   GroupType.FRIENDS),
    SHOPPING("shopping_cart",       Icons.Default.ShoppingCart,   CategoryTint.PINK,   GroupType.OTHER),
    SCHOOL("school",                Icons.Default.School,         CategoryTint.BLUE,   GroupType.OTHER),
    SPORTS("sports",                Icons.Default.Sports,         CategoryTint.BLUE,   GroupType.OTHER),
    WORK("work",                    Icons.Default.Work,           CategoryTint.ORANGE, GroupType.OTHER),
    MONEY("attach_money",           Icons.Default.AttachMoney,    CategoryTint.GREEN,  GroupType.OTHER),
    GENERIC("generic",              Icons.Default.Group,          CategoryTint.ORANGE, GroupType.OTHER);

    companion object {
        /**
         * Lookup by either the DB icon key (e.g. "flight") or a [GroupType]
         * fallback (e.g. "home"). Defaults to [GENERIC] for null/unknown.
         */
        fun fromKey(key: String?): GroupCategory {
            if (key.isNullOrBlank()) return GENERIC
            val normalized = key.lowercase()
            return entries.firstOrNull { it.iconKey == normalized }
                ?: fromGroupType(GroupType.fromDb(normalized))
        }

        fun fromGroupType(type: GroupType): GroupCategory = when (type) {
            GroupType.TRIP    -> FLIGHT
            GroupType.HOME    -> HOME
            GroupType.FRIENDS -> RESTAURANT
            GroupType.COUPLE  -> GIFT
            GroupType.OTHER   -> GENERIC
        }
    }
}

/**
 * Visual tint applied to a category. Kept as a domain enum (not a Compose
 * Color) so the rendering layer can choose the actual swatch from the theme.
 */
enum class CategoryTint { BLUE, GREEN, ORANGE, PINK }
