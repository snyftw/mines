plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.snyftw.mines"; compileSdk = 35
    defaultConfig { applicationId = "com.snyftw.mines"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
