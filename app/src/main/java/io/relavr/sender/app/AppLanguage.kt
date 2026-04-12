package io.relavr.sender.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(
    val tag: String,
) {
    English("en"),
    SimplifiedChinese("zh-CN"),
}

fun currentAppLanguage(): AppLanguage {
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when {
        currentTag.startsWith(AppLanguage.SimplifiedChinese.tag, ignoreCase = true) -> AppLanguage.SimplifiedChinese
        else -> AppLanguage.English
    }
}

fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}
