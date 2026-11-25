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

# Shizuku user service is instantiated by class name in the shell process
-keep class com.sanelink.app.shizuku.ShellService { *; }
-keep class com.sanelink.app.shizuku.IShellService** { *; }
-keep class com.sanelink.app.shizuku.IClipboardSignalCallback** { *; }
