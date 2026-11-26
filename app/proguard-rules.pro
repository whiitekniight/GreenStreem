# Persisted playlist JSON uses field names from pre-optimization releases.
# jcifs uses SLF4J's optional binding; absent bindings intentionally use its NOP fallback.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep only this reflection model, not the whole application package.
-keep,allowoptimization,allowobfuscation class com.example.greenstreem.PlaylistProfile
-keepclassmembers,allowoptimization class com.example.greenstreem.PlaylistProfile {
    <fields>;
}

# Gson creates provider models reflectively, including nested generic responses.
-keep,allowoptimization,allowobfuscation class com.example.greenstreem.Xtream* {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowoptimization class com.example.greenstreem.XtreamVodStream {
    java.lang.String directUrl;
}
-keepclassmembers,allowoptimization class com.example.greenstreem.XtreamEpisode {
    java.lang.String directUrl;
}

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
