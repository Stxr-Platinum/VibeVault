# VibeVault ProGuard Rules

# Supabase / Ktor
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vibevault.app.**$$serializer { *; }
-keepclassmembers class com.vibevault.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.vibevault.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Missing classes identified by R8
-dontwarn com.fasterxml.jackson.databind.deser.std.StdDeserializer
-dontwarn com.fasterxml.jackson.databind.ser.std.StdSerializer
-dontwarn com.spotify.base.annotations.NotNull

# Rhino / Mozilla JavaScript & Java Beans
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-keep class org.mozilla.javascript.** { *; }

# JSoup / RE2J
-dontwarn com.google.re2j.**
-dontwarn org.jsoup.**

# Gson & Generic Signatures
-keepattributes Signature
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-dontwarn sun.misc.**
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Search Cache & Domain Models Serialized by Gson
-keep class com.vibevault.app.domain.model.** { *; }
-keep class com.vibevault.app.ui.screens.search.CachedAlbumItem { *; }
-keep class com.vibevault.app.ui.screens.search.CachedSuggestionsPayload { *; }
-keep class com.vibevault.app.ui.screens.search.suggestions.SuggestionTrack { *; }
-keep class com.vibevault.app.ui.screens.search.suggestions.SuggestionArtist { *; }
-keep class com.vibevault.app.ui.screens.search.suggestions.SuggestionAlbum { *; }
-keep class com.vibevault.app.ui.screens.search.suggestions.SearchSuggestionViewState { *; }
-keep class com.music.innertube.models.AlbumItem { *; }
-keep class com.music.innertube.models.Artist { *; }
-keep class com.music.innertube.models.YTItem { *; }
-keep class com.music.innertube.pages.SearchSummaryPage { *; }
-keep class com.music.innertube.pages.SearchSummary { *; }
