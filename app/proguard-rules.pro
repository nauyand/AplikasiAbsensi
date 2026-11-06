# NAUY Personal Hub
# Room, WorkManager, kotlinx.serialization and Play Services ship their own consumer rules.

# osmdroid (map picker)
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# Backup model classes are (de)serialized by kotlinx.serialization
-keep,includedescriptorclasses class com.teknosolvo.personalhub.backup.**$$serializer { *; }
-keepclassmembers class com.teknosolvo.personalhub.backup.** {
    *** Companion;
}
