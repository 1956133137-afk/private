# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- General Android --
-dontwarn android.os.ServiceManager

# --- Kotlin Coroutines ---
# Keep suspend functions' continuation for reflection.
-keepattributes Signature
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.ContinuationImpl {
    <fields>;
    <init>(...);
}
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    <init>(...);
    <fields>;
}

# --- Gson Rules --
# Keep attributes required for reflection.
-keepattributes InnerClasses
# Keep all fields in any class that are annotated with @SerializedName.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Retrofit Rules --
# Keep Retrofit interfaces and their methods.
-keepclassmembers interface ** {
    @retrofit2.http.* <methods>;
}

# --- Project Specific Data Models (as a safeguard) --
-keep class com.example.storechat.model.** { *; }
-keep class com.example.storechat.data.api.** { *; }

# --- Library Warnings --
-dontwarn okhttp3.**
-dontwarn okio.**
