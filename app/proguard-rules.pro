# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------- Coil 3：解码器通过 ServiceLoader 注册，R8 不能删 ----------
-keep class coil3.video.** { *; }
-keep class coil3.gif.** { *; }
-keep class coil3.service.** { *; }
-keepnames class coil3.** { *; }

# ---------- Media3 / ExoPlayer ----------
-dontwarn androidx.media3.**

# ---------- 应用内被反射/序列化用到的数据类 ----------
-keep class com.zhexian.douyingallery.data.db.** { *; }
-keep class com.zhexian.douyingallery.domain.** { *; }

# 保留行号，崩溃日志可读
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
