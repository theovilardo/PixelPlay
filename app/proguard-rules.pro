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
-dontwarn org.eclipse.jetty.npn.NextProtoNego

# Keep all model classes
-keep class com.theveloper.pixelplay.data.model.** { *; }
-keep class com.theveloper.pixelplay.data.database.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <fields>;
}

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Media3 classes
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Room classes
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep NewPipe Extractor classes
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**

# Keep Retrofit/OkHttp classes
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Ktor server classes
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**

# Keep TagLib classes
-keep class com.kyant.taglib.** { *; }
-keep class org.jaudiotagger.** { *; }
-dontwarn com.kyant.taglib.**
-dontwarn org.jaudiotagger.**

# Keep VorbisJava classes
-keep class org.vorbis.** { *; }
-dontwarn org.vorbis.**

# Keep Amplituda classes
-keep class com.github.psambit9.** { *; }
-dontwarn com.github.psambit9.**

# Keep Checker framework
-keep class dev.icerock.** { *; }
-dontwarn dev.icerock.**

# Keep Timber logging
-dontwarn timber.log.**