-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class app.getyoink.downloader.** { *; }
# ExtraFieldUtils instantiates ZIP extra fields reflectively while extracting
# the bundled Python/FFmpeg runtime. R8 cannot see those constructor calls.
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
