package app.linkedin.extension;

import android.content.SharedPreferences;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;

public class LanguageCompatibilityTest {
    @Test
    public void aNonTextLanguageImportedByAnOldBackupFallsBackSafely() throws Exception {
        SharedPreferences corrupt = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getString")) throw new ClassCastException("boolean language");
                    return null;
                });
        Field preferences = Settings.class.getDeclaredField("prefs");
        preferences.setAccessible(true);
        Object saved = preferences.get(null);
        try {
            preferences.set(null, corrupt);
            assertEquals("en", I18n.resolveLanguage(Settings.language(), Locale.JAPANESE));
        } finally {
            preferences.set(null, saved);
        }
    }

    @Test
    public void englishRemainsTheDefaultOnOtherDeviceLanguages() {
        assertEquals("en", I18n.resolveLanguage(Settings.DEFAULT_LANGUAGE, Locale.forLanguageTag("id-ID")));
        assertEquals("en", I18n.resolveLanguage(Settings.DEFAULT_LANGUAGE, Locale.forLanguageTag("ja-JP")));
        assertNull(Translations.forLanguage("en"));
    }

    @Test
    public void explicitlySelectedLanguagesOverrideDeviceLocale() {
        assertEquals("de", I18n.resolveLanguage("de", Locale.ENGLISH));
        assertEquals("ja", I18n.resolveLanguage("ja", Locale.forLanguageTag("id-ID")));
    }

    @Test
    public void automaticLanguageRetainsUpstreamRegionalFallbacks() {
        assertEquals("pt-BR", I18n.resolveLanguage(I18n.LANGUAGE_AUTO, Locale.forLanguageTag("pt-PT")));
        assertEquals("id", I18n.resolveLanguage(I18n.LANGUAGE_AUTO, Locale.forLanguageTag("id-ID")));
        assertEquals("en", I18n.resolveLanguage(I18n.LANGUAGE_AUTO, Locale.forLanguageTag("pa-IN")));
    }

    @Test
    public void invalidImportedLanguageSettingsFallBackToEnglish() {
        assertEquals("en", I18n.resolveLanguage("missing", Locale.GERMAN));
        assertEquals("en", I18n.resolveLanguage(null, Locale.JAPANESE));
    }

    @Test
    public void compiledTranslationsRetainEnglishKeysAndFormatArguments() {
        for (String language : Translations.LANGUAGES.keySet()) {
            if (language.equals("en")) continue;
            Map<String, String> table = Translations.forLanguage(language);
            assertNotNull(language, table);
            assertNotNull(language, table.get("Copy diagnostic report"));
            String formatted = String.format(Locale.ROOT, table.get("Photos: %1$s\nVideos: %2$s"), "Pictures", "Movies");
            assertTrue(language, formatted.contains("Pictures"));
            assertTrue(language, formatted.contains("Movies"));
        }
    }
}
