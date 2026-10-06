# kotlinx.serialization keeps its own rules in the library.

# libwebrtc calls into its Java classes from native code (JNI) and its AAR ships no consumer
# rules, so R8 must not rename or remove any of them.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
