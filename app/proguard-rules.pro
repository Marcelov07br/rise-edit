# OpenCV JNI looks up classes, methods and nativeObj by their original names.
-keep class org.opencv.** { *; }
# Preserve the app's public model and AndroidViewModel reflection contract for this first R8 release.
-keep class br.com.riseedit.** { *; }
# The instrumentation APK shares app dependencies and needs these public APIs before tests can start.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
