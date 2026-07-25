-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.wikitide.wikiportal.**$$serializer { *; }
-keepclassmembers class org.wikitide.wikiportal.** {
    *** Companion;
}
-keepclasseswithmembers class org.wikitide.wikiportal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
