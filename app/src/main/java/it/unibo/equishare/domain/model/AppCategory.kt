/** Defines the App Category domain model. */
package it.unibo.equishare.domain.model

data class AppCategory(
    val id: String,
    val code: String,
    val translations: Map<String, String>,
    val iconKey: String,
    val groupType: GroupType,
    val sortOrder: Int,
) {
    val category: GroupCategory get() = GroupCategory.fromKey(iconKey)

    fun localizedName(language: String): String =
        translations[language]?.takeIf { it.isNotBlank() }
            ?: translations.values.firstOrNull { it.isNotBlank() }
            ?: code
}
