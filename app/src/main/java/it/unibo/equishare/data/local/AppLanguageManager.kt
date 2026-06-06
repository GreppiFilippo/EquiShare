/** Manages app language selection and localized resources. */
package it.unibo.equishare.data.local

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import it.unibo.equishare.R
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import org.xmlpull.v1.XmlPullParser

class AppLanguageManager(
    private val context: Context,
    private val userPreferences: UserPreferencesDataSource,
) {
    /** Reads supported app languages from locales_config.xml. */
    val supportedLanguageTags: List<String> by lazy {
        readSupportedLanguageTags(context)
    }

    val languageTag: Flow<String?> = userPreferences.languageTag

    fun currentSystemAppLanguageTag(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].toLanguageTag()
    }

    suspend fun syncWithSystemAppLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            userPreferences.setLanguageTag(currentSystemAppLanguageTag())
        }
    }

    /** Applies and persists the selected app language. */
    suspend fun setLanguageTag(languageTag: String?) {
        val normalizedTag = languageTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { requestedTag ->
                supportedLanguageTags.firstOrNull { it.equals(requestedTag, ignoreCase = true) }
                    ?: requestedTag
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (normalizedTag == null) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalizedTag)
            }
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        }

        userPreferences.setLanguageTag(normalizedTag)
    }

    fun localizedContext(base: Context, languageTag: String?): Context {
        val locale = if (languageTag.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = base.getSystemService(LocaleManager::class.java).applicationLocales
                if (!locales.isEmpty) locales[0] else base.resources.configuration.locales[0]
            } else {
                base.resources.configuration.locales[0]
            }
        } else {
            Locale.forLanguageTag(languageTag)
        }

        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }

        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(configuration)
    }

    private fun readSupportedLanguageTags(context: Context): List<String> {
        val parser = context.resources.getXml(R.xml.locales_config)
        return parser.use { parser ->
            buildList {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        val tag = parser.getAttributeValue(ANDROID_NAMESPACE, "name")
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                        if (tag != null && none { it.equals(tag, ignoreCase = true) }) {
                            add(tag)
                        }
                    }
                }
            }
        }
    }

    fun resources(languageTag: String?): android.content.res.Resources {
        return localizedContext(context, languageTag).resources
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
