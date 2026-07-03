# Keep kotlinx.serialization generated serializers
-keepclassmembers class com.fundingledger.** {
    *** Companion;
}
-keepclasseswithmembers class com.fundingledger.** {
    kotlinx.serialization.KSerializer serializer(...);
}
