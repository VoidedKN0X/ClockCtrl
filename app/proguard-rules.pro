# kotlinx.serialization models (library ships its own rules; these keep saved history and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.clockctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.clockctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.clockctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Google-free: Guava's listenablefuture jar is excluded from the build (see app/build.gradle.kts).
# AndroidX's concurrent-futures still names its interface in classes ClockCtrl never uses.
-dontwarn com.google.common.util.concurrent.ListenableFuture
