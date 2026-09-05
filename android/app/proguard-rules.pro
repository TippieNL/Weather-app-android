# kotlinx.serialization keeps generated serializers reachable via companion objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.weatherquips.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.weatherquips.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces are accessed reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# osmdroid reads configuration reflectively from its own package.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
