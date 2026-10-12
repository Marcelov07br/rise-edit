# OpenCV JNI looks up classes, methods and nativeObj by their original names.
-keep class org.opencv.** { *; }
# Preserve the app's public model and AndroidViewModel reflection contract for this first R8 release.
-keep class br.com.riseedit.** { *; }
# The instrumentation APK shares app dependencies and needs these public APIs before tests can start.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Preserve APIs called across the separate instrumentation APK (including their method descriptors).
-keep class androidx.media3.exoplayer.ExoPlayer$Builder { public *; }
-keep interface androidx.media3.exoplayer.ExoPlayer { *; }
-keep interface androidx.media3.exoplayer.analytics.AnalyticsListener { *; }
-keep class androidx.media3.effect.DebugTraceUtil { public *; }
-keep class androidx.lifecycle.ViewModelProvider** { *; }
-keep interface androidx.media3.common.Player** { *; }
-keepclassmembers class androidx.activity.ComponentActivity { public *; }
-keep class androidx.media3.common.Timeline$Window { public *; }
-keepclassmembers class androidx.media3.transformer.CompositionPlayer { public *; }
-keep class androidx.media3.transformer.Transformer$Builder { public *; }
-keep class androidx.lifecycle.Lifecycle** { public protected *; }
