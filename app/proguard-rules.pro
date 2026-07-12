# Keep kotlinx.serialization serializers for our own model classes.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.provender.** {
    *** Companion;
}
-keepclasseswithmembers class com.provender.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit is wired but unused until later phases; safe defaults.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
