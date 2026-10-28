package com.red.sovereign.settings

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * مدير لغة التطبيق — بديل الإعداد «المقفل» الذي كان يقول:
 * `LockedSetting("RTL والعربية", "مفعلة تلقائيًا حسب لغة النظام")` أي أن المستخدم
 * لا يملك أي تحكم في اللغة رغم وجود `res/xml/locales_config.xml` (ar + en) و
 * `android:localeConfig` في المانيفست.
 *
 * ## المنهج
 * - Android 13+ (API 33): `LocaleManager.setApplicationLocales` — المسار الرسمي
 *   لإعدادات «لغة التطبيق» في النظام، ويعمل تلقائياً مع كل الأنشطة.
 * - أقدم من 33: `AppCompatDelegate.setApplicationLocales` (appcompat 1.7.1)
 *   مع تحديث `Configuration` يدوياً وإعادة إنشاء النشاط، لأن MainActivity يرث
 *   FragmentActivity لا AppCompatActivity.
 * - الاختيار يُحفظ محلياً في SharedPreferences ويُعاد تطبيقه عند كل إقلاع
 *   عبر [initialize] من `YounesApplication`.
 *
 * ## حدود صادقة
 * معظم نصوص Compose مضمّنة حالياً داخل الشاشات (لا `@string`). هذا المدير
 * يطبّق اللغة على موارد XML (اسم التطبيق، الإشعارات، قنوات النظام، وشاشات
 * الأذونات) فوراً؛ ترحيل بقية النصوص إلى `strings.xml` مفصل في خطة العمل.
 */
object LanguageManager {
    const val SYSTEM = "system"
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    private const val PREFS = "younes_language"
    private const val KEY = "tag"

    val options: List<Pair<String, String>> = listOf(
        SYSTEM to "حسب النظام",
        ARABIC to "العربية",
        ENGLISH to "English"
    )

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM

    /** يُستدعى مرة واحدة من Application.onCreate قبل أول نشاط. */
    fun initialize(context: Context) {
        val tag = current(context)
        if (tag == SYSTEM) return
        applyInternal(context, tag, recreate = false)
    }

    fun apply(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag).apply()
        if (tag == SYSTEM) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching {
                    context.getSystemService(android.app.LocaleManager::class.java)
                        .setApplicationLocales(LocaleList.getEmptyLocaleList())
                }
            }
            runCatching { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        } else {
            applyInternal(context, tag, recreate = true)
        }
        findActivity(context)?.recreate()
    }

    /** يفكّ أغلفة ContextThemeWrapper حتى يصل للنشاط الحقيقي. */
    private fun findActivity(context: Context): Activity? {
        var c: Context? = context
        while (c != null) {
            if (c is Activity) return c
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    private fun applyInternal(context: Context, tag: String, recreate: Boolean) {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.getSystemService(android.app.LocaleManager::class.java)
                    .setApplicationLocales(LocaleList.forLanguageTags(tag))
            }
        }
        runCatching {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
        @Suppress("DEPRECATION")
        runCatching {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
        // recreate تتم من [apply] لتجنب إعادة الإنشاء عند الإقلاع.
    }

    /** اتجاه التخطيط الحالي — يفيد الواجهات التي ترسم أيقونات اتجاهية. */
    fun isRtl(context: Context): Boolean {
        val tag = current(context)
        val resolved = if (tag == SYSTEM) Locale.getDefault().language else tag
        return resolved == ARABIC
    }
}
