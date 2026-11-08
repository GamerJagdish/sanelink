# SaneLink Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep serializable rule models
-keep class com.sanelink.app.core.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclasseswithmembers class * {
    companion object;
}
