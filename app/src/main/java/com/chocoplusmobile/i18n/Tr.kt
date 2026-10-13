package com.chocoplusmobile.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.chocoplusmobile.state.AppLanguage
import com.chocoplusmobile.state.LocalAppViewModel

/**
 * Every translatable UI string the app's own screens use (as opposed to
 * [ContentTranslations], which covers backend-taxonomy keys like genres and
 * crew jobs) — one enum entry per piece of copy, added screen by screen as
 * each one is converted off hardcoded French text. Call [t] from a
 * composable to resolve one against the current Settings > language.
 *
 * Keys are named SCREEN_PURPOSE rather than by their French text, since the
 * French text is just one of three translations, not the source of truth.
 */
enum class Tr {
    // ── Settings ──
    SETTINGS_TITLE,
    SETTINGS_SECTION_LANGUAGE,
    SETTINGS_LANGUAGE_APP_TITLE,
    SETTINGS_LANGUAGE_APP_DESC,
    SETTINGS_SECTION_DISPLAY,
    SETTINGS_FORMAT_PAGE_TITLE,
    SETTINGS_FORMAT_PAGE_DESC,
    PAGE_HOME,
    PAGE_FILMS,
    PAGE_SERIES,
    PAGE_CATALOGUE,
    PAGE_SEARCH,
    PAGE_MYLIST,
    PAGE_PROFILE,
    FORMAT_VERTICAL,
    FORMAT_HORIZONTAL,
    SETTINGS_IMAGE_QUALITY_TITLE,
    SETTINGS_IMAGE_QUALITY_DESC,
    SETTINGS_HERO_QUALITY_TITLE,
    SETTINGS_HERO_QUALITY_DESC,
    QUALITY_LOW,
    QUALITY_LOW_DESC,
    QUALITY_MEDIUM,
    QUALITY_MEDIUM_DESC,
    QUALITY_HIGH,
    QUALITY_HIGH_DESC,
    QUALITY_ORIGINAL,
    QUALITY_ORIGINAL_DESC,
    SETTINGS_LOGO_OR_TITLE_TITLE,
    SETTINGS_LOGO_OR_TITLE_DESC,
    LOGO,
    TITLE_LABEL,
    SETTINGS_AUTOPLAY_LABEL,
    SETTINGS_AUTOPLAY_DESC,
    SETTINGS_SECTION_START,
    SETTINGS_START_PAGE_TITLE,
    SETTINGS_SECTION_PLAYBACK,
    SETTINGS_DECODING_TITLE,
    SETTINGS_DECODING_DESC,
    DECODING_SOFTWARE,
    DECODING_HARDWARE,
}

private fun tr(fr: String, en: String, ja: String): Map<AppLanguage, String> = mapOf(AppLanguage.FR to fr, AppLanguage.EN to en, AppLanguage.JA to ja)

private val TRANSLATIONS: Map<Tr, Map<AppLanguage, String>> = mapOf(
    Tr.SETTINGS_TITLE to tr("Paramètres", "Settings", "設定"),
    Tr.SETTINGS_SECTION_LANGUAGE to tr("LANGUE", "LANGUAGE", "言語"),
    Tr.SETTINGS_LANGUAGE_APP_TITLE to tr("Langue de l'application", "App language", "アプリの言語"),
    Tr.SETTINGS_LANGUAGE_APP_DESC to tr(
        "Envoyée à chaque requête, y compris pour les titres et descriptions.",
        "Sent with every request, including for titles and descriptions.",
        "タイトルや説明文を含む、すべてのリクエストに送信されます。",
    ),
    Tr.SETTINGS_SECTION_DISPLAY to tr("AFFICHAGE", "DISPLAY", "表示"),
    Tr.SETTINGS_FORMAT_PAGE_TITLE to tr("Format des affiches", "Poster format", "ポスターの形式"),
    Tr.SETTINGS_FORMAT_PAGE_DESC to tr(
        "Mémorisé indépendamment pour chaque page.",
        "Remembered separately for each page.",
        "ページごとに個別に記憶されます。",
    ),
    Tr.PAGE_HOME to tr("Accueil", "Home", "ホーム"),
    Tr.PAGE_FILMS to tr("Films", "Movies", "映画"),
    Tr.PAGE_SERIES to tr("Séries", "Series", "シリーズ"),
    Tr.PAGE_CATALOGUE to tr("Catalogue", "Catalogue", "カタログ"),
    Tr.PAGE_SEARCH to tr("Recherche", "Search", "検索"),
    Tr.PAGE_MYLIST to tr("Ma liste", "My List", "マイリスト"),
    Tr.PAGE_PROFILE to tr("Profil", "Profile", "プロフィール"),
    Tr.FORMAT_VERTICAL to tr("Vertical", "Vertical", "縦"),
    Tr.FORMAT_HORIZONTAL to tr("Horizontal", "Horizontal", "横"),
    Tr.SETTINGS_IMAGE_QUALITY_TITLE to tr("Qualité des affiches", "Poster quality", "ポスターの画質"),
    Tr.SETTINGS_IMAGE_QUALITY_DESC to tr(
        "Affiches, vignettes et photos de casting — dans les listes et grilles.",
        "Posters, thumbnails and cast photos — in lists and grids.",
        "ポスター、サムネイル、キャスト写真 — リストとグリッドで使用されます。",
    ),
    Tr.SETTINGS_HERO_QUALITY_TITLE to tr("Qualité des en-têtes", "Header quality", "ヘッダーの画質"),
    Tr.SETTINGS_HERO_QUALITY_DESC to tr(
        "Fond et logo à la une (Accueil, Films, Séries) et fond des fiches détails.",
        "Featured background and logo (Home, Movies, Series) and detail page backgrounds.",
        "特集の背景とロゴ（ホーム、映画、シリーズ）、詳細ページの背景に使用されます。",
    ),
    Tr.QUALITY_LOW to tr("Économie", "Data saver", "節約"),
    Tr.QUALITY_LOW_DESC to tr("Le moins de données, images plus légères", "Least data, lighter images", "データ量最小、軽量な画像"),
    Tr.QUALITY_MEDIUM to tr("Standard", "Standard", "標準"),
    Tr.QUALITY_MEDIUM_DESC to tr("Bon compromis qualité / données", "Good balance of quality and data", "画質とデータ量のバランスが良い"),
    Tr.QUALITY_HIGH to tr("Élevée", "High", "高画質"),
    Tr.QUALITY_HIGH_DESC to tr("Belles affiches, plus de données", "Great-looking posters, more data", "美しいポスター、データ量は多め"),
    Tr.QUALITY_ORIGINAL to tr("Originale", "Original", "オリジナル"),
    Tr.QUALITY_ORIGINAL_DESC to tr("Qualité maximale, sans compression", "Maximum quality, uncompressed", "圧縮なしの最高画質"),
    Tr.SETTINGS_LOGO_OR_TITLE_TITLE to tr("Logo ou titre", "Logo or title", "ロゴまたはタイトル"),
    Tr.SETTINGS_LOGO_OR_TITLE_DESC to tr(
        "Quand le logo du média n'existe pas, le titre s'affiche quand même.",
        "When a title has no logo artwork, its title text is shown instead.",
        "ロゴ画像がない場合は、代わりにタイトルが表示されます。",
    ),
    Tr.LOGO to tr("Logo", "Logo", "ロゴ"),
    Tr.TITLE_LABEL to tr("Titre", "Title", "タイトル"),
    Tr.SETTINGS_AUTOPLAY_LABEL to tr("Vidéo tournante automatique", "Auto-playing hero video", "ヒーロー動画の自動再生"),
    Tr.SETTINGS_AUTOPLAY_DESC to tr("En-tête animé sur Films et Séries", "Animated header on Movies and Series", "映画・シリーズページのアニメーションヘッダー"),
    Tr.SETTINGS_SECTION_START to tr("DÉMARRAGE", "STARTUP", "起動"),
    Tr.SETTINGS_START_PAGE_TITLE to tr("Page d'ouverture de l'app", "App opening page", "アプリの起動ページ"),
    Tr.SETTINGS_SECTION_PLAYBACK to tr("LECTURE", "PLAYBACK", "再生"),
    Tr.SETTINGS_DECODING_TITLE to tr("Décodage vidéo", "Video decoding", "動画デコード"),
    Tr.SETTINGS_DECODING_DESC to tr(
        "Logiciel : couleurs fidèles sur tous les films, plus gourmand en batterie. Matériel : plus économe, mais colorimétrie parfois délavée selon la source.",
        "Software: accurate colors on every title, heavier on battery. Hardware: lighter on battery, but colors can look washed out depending on the source.",
        "ソフトウェア：どの作品でも正確な色再現、バッテリー消費は大きめ。ハードウェア：省電力ですが、ソースによって色が薄くなることがあります。",
    ),
    Tr.DECODING_SOFTWARE to tr("Logiciel", "Software", "ソフトウェア"),
    Tr.DECODING_HARDWARE to tr("Matériel", "Hardware", "ハードウェア"),
)

/** Resolves [key] against Settings > language, recomposing automatically
 * when it changes. Falls back to French if a key is somehow missing a
 * translation for the current language (should never happen — every entry
 * above carries all three). */
@Composable
fun t(key: Tr): String {
    val appViewModel = LocalAppViewModel.current
    val prefs by appViewModel.prefs.collectAsState()
    val entry = TRANSLATIONS.getValue(key)
    return entry[prefs.settings.language] ?: entry.getValue(AppLanguage.FR)
}
