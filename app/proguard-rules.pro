# Cipherly keeps application-specific R8 rules here.
# Never add rules that log, expose, or persist cryptographic material.

# SQLCipher uses JNI and reflection.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
