# SaneLink Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Serializable rule models (stored as JSON in DataStore and parsed from rules/patches.json).
# The ** wildcard also keeps their generated $serializer and Companion classes.
-keep class com.sanelink.app.core.model.** { *; }

# Shizuku user service is instantiated by class name in the shell process
-keep class com.sanelink.app.shizuku.ShellService { *; }
-keep class com.sanelink.app.shizuku.IShellService** { *; }
-keep class com.sanelink.app.shizuku.IClipboardSignalCallback** { *; }
