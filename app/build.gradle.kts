import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}


// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.remover.background.AI"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.remover.background.AI"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Keep all language resources (prevent Play Store from stripping them)
        resourceConfigurations += listOf(
            "en", "es", "fr", "de", "hi", "zh", 
            "pt-rBR", "in", "ja", "ko", "ar", "tr"
        )
        
        // AdMob IDs from local.properties (fallback to test IDs for development)
        buildConfigField("String", "ADMOB_APP_ID", "\"${localProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")}\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${localProperties.getProperty("ADMOB_BANNER_ID", "ca-app-pub-3940256099942544/6300978111")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${localProperties.getProperty("ADMOB_INTERSTITIAL_ID", "ca-app-pub-3940256099942544/1033173712")}\"")
        
        // AdMob App ID for manifest
        manifestPlaceholders["ADMOB_APP_ID"] = localProperties.getProperty("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    }

    buildTypes {
        release {
            // Enable code shrinking, obfuscation, and optimization
            isMinifyEnabled = true
            // Enable resource shrinking
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    // Disable language splitting in App Bundle - keep all languages
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // Image Loading
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Permissions
    implementation(libs.accompanist.permissions)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Datastore
    implementation(libs.androidx.datastore.preferences)

    // In-App Updates
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // In-App Review
    implementation("com.google.android.play:review:2.0.1")
    implementation("com.google.android.play:review-ktx:2.0.1")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    // SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)


    debugImplementation(libs.androidx.ui.test.manifest)

    // MLKit Subject Segmentation (Background Removal)
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")

}