-keep class com.junnz.shared.domain.** { *; }
-keep class com.junnz.shared.ipc.** { *; }
-keepattributes *Annotation*
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}
