# kotlinx.serialization: сериализаторы ищутся через сгенерированные companion-методы.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nutritiontracker.**$$serializer { *; }
-keepclassmembers class com.nutritiontracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.nutritiontracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit: интерфейсы API используются через рефлексию.
-keep,allowobfuscation,allowshrinking interface com.nutritiontracker.remote.usda.UsdaApi
-keep,allowobfuscation,allowshrinking interface com.nutritiontracker.remote.off.OffApi
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Модели ответов API разбираются kotlinx.serialization.
-keep class com.nutritiontracker.remote.usda.** { *; }
-keep class com.nutritiontracker.remote.off.** { *; }
-keep class com.nutritiontracker.core.backup.** { *; }
