# ONNX Runtime and JNI entry points are reflected by native/runtime code.
-keep class ai.onnxruntime.** { *; }
-keep class com.localmed.ai.tokenizer.NativeTokenizerBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Keep protobuf generated schemas used for canonical record persistence.
-keep class com.localmed.protocol.** extends com.google.protobuf.GeneratedMessageLite { *; }
