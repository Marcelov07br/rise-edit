# OpenCV JNI looks up classes, methods and nativeObj by their original names.
-keep class org.opencv.** { *; }
# Preserve the app's public model and AndroidViewModel reflection contract for this first R8 release.
-keep class br.com.riseedit.** { *; }
# AndroidJUnitRunner shares this app dependency; its reflective startup needs the original tracing API.
-keep class androidx.tracing.** { *; }
