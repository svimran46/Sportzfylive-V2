# Sportzfy release rules. Media3 and Coil ship consumer rules; Phase 6 adds
# targeted rules after the first R8 build is verified.

# kotlinx.serialization: keep generated serializers for DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.sportzfy.live.data.model.**$$serializer { *; }
-keepclassmembers class com.sportzfy.live.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.sportzfy.live.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
