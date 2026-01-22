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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Disable obfuscation - keep class/method names readable
# (App is open source, no need to hide code)
-dontobfuscate

# Keep javax.lang.model classes (often needed by annotation processors or code generation libraries)
-keep class javax.lang.model.** { *; }
-keep interface javax.lang.model.** { *; }

# Keep javax.sound.sampled classes (for audio processing libraries like JFLAC)
-keep class javax.sound.sampled.** { *; }
-keep interface javax.sound.sampled.** { *; }

# Specific rules for JavaPoet if the above is not enough
-keep class com.squareup.javapoet.** { *; }
-keep interface com.squareup.javapoet.** { *; }

# Specific rules for AutoValue if it's directly used or a transitive dependency
# (though usually AutoValue is a compile-time dependency and shouldn't need this)
# -keep class com.google.auto.value.** { *; }
# -keep interface com.google.auto.value.** { *; }

# Rules for TagLib
-keep class com.kyant.taglib.** { *; }

# [NUEVO] Regla general para mantener metadatos de Kotlin, puede ayudar a R8
-keep class kotlin.Metadata { *; }

# ExoPlayer FFmpeg extension
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.

# [NUEVO] Reglas para solucionar el error de Ktor y R8
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**

-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.sound.sampled.AudioFileFormat$Type
-dontwarn javax.sound.sampled.AudioFileFormat
-dontwarn javax.sound.sampled.AudioFormat$Encoding
-dontwarn javax.sound.sampled.AudioFormat
-dontwarn javax.sound.sampled.AudioInputStream
-dontwarn javax.sound.sampled.UnsupportedAudioFileException
-dontwarn javax.sound.sampled.spi.AudioFileReader
-dontwarn javax.sound.sampled.spi.FormatConversionProvider
-dontwarn javax.swing.filechooser.FileFilter

-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego

# TDLib (Telegram Database Library) rules
-keep class org.drinkless.tdlib.** { *; }
-keep interface org.drinkless.tdlib.** { *; }

# Ktor & Netty Rules (Crucial for StreamProxy)
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.slf4j.** { *; }

# Ktor Specific
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn io.netty.**

# Reflection usage in Ktor/Netty
-keepnames class io.ktor.** { *; }
-keepnames class io.netty.** { *; }

# Ensure internal server can start
-keep class com.theveloper.pixelplay.data.telegram.TelegramStreamProxy { *; }

-keep class com.theveloper.pixelplay.data.telegram.** { *; }
-keep interface com.theveloper.pixelplay.data.telegram.** { *; }

# Keep Kotlin reflection if needed by Ktor/Serialization in Release
-keep class kotlin.reflect.** { *; }

# =============================================================================
# TIMBER LOGGING OPTIMIZATION FOR RELEASE BUILDS
# =============================================================================
# Strip VERBOSE and DEBUG log calls entirely from release builds.
# This removes the method calls at bytecode level, eliminating any overhead
# from string concatenation or log message building.

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}

# Also strip Timber.Tree methods used by custom trees (belt and suspenders)
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}

# Strip Android Log.v and Log.d calls as well
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}