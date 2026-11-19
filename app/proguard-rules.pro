-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# kotlinx.serialization
-keep,includedescriptorclasses class com.pratclot.cafes.**$$serializer { *; }
-keepclassmembers class com.pratclot.cafes.** {
    *** Companion;
}
-keepclasseswithmembers class com.pratclot.cafes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
