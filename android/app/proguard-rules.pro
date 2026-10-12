# Sportzfy release (R8) rules.
#
# Most libraries ship their own consumer rules, which R8 applies automatically:
# Media3 (ExoPlayer, HLS), Coil 3, OkHttp, Retrofit, kotlinx.serialization,
# Koin, Navigation and Compose. The rules below cover only what is specific
# to this app.

# Keep source file names and line numbers so release crash traces map back
# to code; the original class names are recovered from mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: wire DTOs and type-safe navigation routes are
# looked up by their generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.sportzfy.live.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.sportzfy.live.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit builds the API from its interface at runtime; keep the service
# methods and the generic signatures (suspend + JsonElement) they rely on.
-keep,allowobfuscation interface com.sportzfy.live.core.network.StreamedApiService
-keepclassmembers interface com.sportzfy.live.core.network.StreamedApiService { *; }

# WebView clients are instantiated by the app but invoked by the platform.
-keep class com.sportzfy.live.presentation.watch.player.** extends android.webkit.WebViewClient { *; }
-keep class com.sportzfy.live.presentation.watch.player.** extends android.webkit.WebChromeClient { *; }
