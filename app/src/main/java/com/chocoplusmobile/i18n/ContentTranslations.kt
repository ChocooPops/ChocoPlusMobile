package com.chocoplusmobile.i18n

import com.chocoplusmobile.state.AppLanguage

/**
 * FR/EN/JA labels for the two backend taxonomies that already arrive as
 * plain uppercase keys — `CategorySimple.translationKey` (genres) and
 * `MediaCredit.job` (TMDB's crew-department taxonomy, anime titles included)
 * — as opposed to [com.chocoplusmobile.i18n.Tr], which covers this app's own
 * UI strings. Looked up case-insensitively by [Adapters.humanizeKey]/
 * [Adapters.humanizeJob], with their existing best-effort formatting kept as
 * a fallback for any key the backend adds later that isn't listed here yet.
 */
object ContentTranslations {
    val categories: Map<String, Map<AppLanguage, String>> = buildMap {
        fun put(key: String, fr: String, en: String, ja: String) = put(key, mapOf(AppLanguage.FR to fr, AppLanguage.EN to en, AppLanguage.JA to ja))

        put("ACTION", "Action", "Action", "アクション")
        put("ADVENTURE", "Aventure", "Adventure", "アドベンチャー")
        put("ANIMATION", "Animation", "Animation", "アニメーション")
        put("COMEDY", "Comédie", "Comedy", "コメディ")
        put("CRIME", "Crime", "Crime", "クライム")
        put("DOCUMENTARY", "Documentaire", "Documentary", "ドキュメンタリー")
        put("DRAMA", "Drame", "Drama", "ドラマ")
        put("FAMILY", "Famille", "Family", "ファミリー")
        put("FANTASY", "Fantastique", "Fantasy", "ファンタジー")
        put("HISTORY", "Histoire", "History", "歴史")
        put("HORROR", "Horreur", "Horror", "ホラー")
        put("MUSIC", "Musique", "Music", "ミュージック")
        put("MYSTERY", "Mystère", "Mystery", "ミステリー")
        put("ROMANCE", "Romance", "Romance", "ロマンス")
        put("SCIENCE FICTION", "Science-fiction", "Science Fiction", "SF")
        put("TV MOVIE", "Téléfilm", "TV Movie", "テレビ映画")
        put("THRILLER", "Thriller", "Thriller", "スリラー")
        put("WAR", "Guerre", "War", "戦争")
        put("WESTERN", "Western", "Western", "西部劇")
        put("ACTION & ADVENTURE", "Action et aventure", "Action & Adventure", "アクション＆アドベンチャー")
        put("KIDS", "Jeunesse", "Kids", "キッズ")
        put("NEWS", "Actualités", "News", "ニュース")
        put("REALITY", "Téléréalité", "Reality", "リアリティ")
        put("SCI-FI & FANTASY", "Science-fiction et fantastique", "Sci-Fi & Fantasy", "SF＆ファンタジー")
        put("SOAP", "Soap", "Soap", "メロドラマ")
        put("TALK", "Talk-show", "Talk", "トーク")
        put("WAR & POLITICS", "Guerre et politique", "War & Politics", "戦争と政治")
    }

    val jobs: Map<String, Map<AppLanguage, String>> = buildMap {
        fun put(key: String, fr: String, en: String, ja: String) = put(key, mapOf(AppLanguage.FR to fr, AppLanguage.EN to en, AppLanguage.JA to ja))

        put("CREDIT", "Crédit", "Credit", "クレジット")
        put("ACTOR", "Acteur", "Actor", "出演")
        put("CREATOR", "Créateur", "Creator", "原作者")
        put("DIRECTOR", "Réalisateur", "Director", "監督")
        put("SCREENPLAY", "Scénariste", "Screenplay", "脚本")
        put("WRITER", "Auteur", "Writer", "作者")
        put("STORY", "Ecriture", "Story", "原案")
        put("ORIGINAL MUSIC COMPOSER", "Compositeur de la musique originale", "Original Music Composer", "音楽")
        put("MUSIC SUPERVISOR", "Superviseur musical", "Music Supervisor", "音楽監修")
        put("PRODUCER", "Producteur", "Producer", "プロデューサー")
        put("DIRECTOR OF PHOTOGRAPHY", "Directeur de la photographie", "Director of Photography", "撮影監督")
        put("ASSISTANT DIRECTOR OF PHOTOGRAPHY", "Assistant directeur de la photographie", "Assistant Director of Photography", "撮影監督補佐")
        put("VFX DIRECTOR OF PHOTOGRAPHY", "Directeur de la photographie des effets visuels", "VFX Director of Photography", "VFX撮影監督")
        put("AERIAL DIRECTOR OF PHOTOGRAPHY", "Directeur de la photographie aérienne", "Aerial Director of Photography", "空撮監督")
        put("CHARACTER DESIGN", "Conception des personnages", "Character Design", "キャラクターデザイン")
        put("SERIES DIRECTOR", "Réalisateur de la série", "Series Director", "シリーズディレクター")
        put("LAYOUT DESIGNER", "Designer de mise en page", "Layout Designer", "レイアウト")
        put("ART DESIGNER", "Designer artistique", "Art Designer", "美術デザイン")
        put("COMIC BOOK", "Auteur original", "Original Comic", "原作(漫画)")
        put("CREATURE DESIGN", "Conception des créatures", "Creature Design", "クリーチャーデザイン")
        put("THEME SONG PERFORMANCE", "Interprétation du générique", "Theme Song Performance", "主題歌")
        put("KEY ANIMATION", "Animateur clé", "Key Animation", "原画")
        put("ANIMATION DIRECTOR", "Directeur de l'animation", "Animation Director", "作画監督")
        put("ORIGINAL STORY", "Histoire originale", "Original Story", "原作")
        put("SUPERVISING ANIMATION DIRECTOR", "Superviseur de l'animation", "Supervising Animation Director", "総作画監督")
        put("ANIMATION SUPERVISOR", "Superviseur animation", "Animation Supervisor", "アニメーション監修")
        put("VISUAL EFFECTS TECHNICAL DIRECTOR", "Directeur technique des effets visuels", "Visual Effects Technical Director", "視覚効果テクニカルディレクター")
        put("SPECIAL EFFECTS SUPERVISOR", "Superviseur des effets spéciaux", "Special Effects Supervisor", "特殊効果監修")
        put("VISUAL EFFECTS SUPERVISOR", "Superviseur des effets visuels", "Visual Effects Supervisor", "視覚効果監修")
        put("VISUAL EFFECTS", "Effets visuels", "Visual Effects", "視覚効果")
        put("ADDITIONAL STORYBOARDING", "Storyboard additionnel", "Additional Storyboarding", "追加絵コンテ")
        put("STORYBOARD ARTIST", "Artiste storyboard", "Storyboard Artist", "絵コンテ")
        put("LEAD CHARACTER DESIGNER", "Responsable du design des personnages", "Lead Character Designer", "キャラクターデザイン責任者")
        put("BACKGROUND DESIGNER", "Designeur des décors", "Background Designer", "背景美術")
        put("MAIN TITLE THEME COMPOSER", "Compositeur du thème principal", "Main Title Theme Composer", "メインテーマ作曲")
        put("OPENING/ENDING ANIMATION", "Animateur d'opening/ending", "Opening/Ending Animation", "オープニング・エンディングアニメーション")
        put("3D ANIMATOR", "Animateur 3D", "3D Animator", "3DCGアニメーター")
        put("3D DIRECTOR", "Réalisateur 3D", "3D Director", "3D監督")
        put("MECHANICAL DESIGNER", "Designer mécanique", "Mechanical Designer", "メカニックデザイン")
        put("SOUND MIXER", "Mixeur son", "Sound Mixer", "録音")
        put("SOUND EFFECTS", "Effets sonores", "Sound Effects", "音響効果")
        put("SOUND DIRECTOR", "Directeur du son", "Sound Director", "音響監督")
        put("SOUND DESIGNER", "Designer sonore", "Sound Designer", "サウンドデザイン")

        // Not in the list the backend gave us directly, but already handled
        // (FR-only) before this table existed — kept rather than dropped, now
        // with EN/JA added too.
        put("EXECUTIVE PRODUCER", "Producteur exécutif", "Executive Producer", "製作総指揮")
        put("EDITOR", "Montage", "Editor", "編集")
        put("ART DIRECTION", "Direction artistique", "Art Direction", "美術")
        put("PRODUCTION DESIGN", "Décors", "Production Design", "プロダクションデザイン")
        put("COSTUME DESIGN", "Costumes", "Costume Design", "衣装デザイン")
        put("CASTING", "Casting", "Casting", "キャスティング")
        put("SOUND", "Son", "Sound", "音響")
        put("MAKEUP ARTIST", "Maquillage", "Makeup Artist", "メイク")
    }
}
