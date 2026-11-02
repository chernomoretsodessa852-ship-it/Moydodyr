plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.moydodyr"; compileSdk = 35
 defaultConfig { applicationId = "com.example.moydodyr"; minSdk = 26; targetSdk = 32; versionCode = 1; versionName = "0.1" } }
dependencies { implementation("com.alphacephei:vosk-android:0.3.47") }
