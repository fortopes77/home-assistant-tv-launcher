# Keep class and method names, so a crash shown in Fortopes Home's settings still reads clearly.
# The code is still shrunk and optimised.
-dontobfuscate

# OkHttp's optional TLS providers aren't on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
