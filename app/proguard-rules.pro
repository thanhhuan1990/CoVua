# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.huanhuynh.covua.**$$serializer { *; }
-keepclassmembers class com.huanhuynh.covua.** {
    *** Companion;
}
-keepclasseswithmembers class com.huanhuynh.covua.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager workers are instantiated by reflection
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# WorkManager/Room: R8 có thể xoá constructor rỗng của database do Room sinh ra (được tạo bằng reflection)
# → crash khi mở app bản release: NoSuchMethodException androidx.work.impl.WorkDatabase_Impl.<init> []
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.** { <init>(...); }
