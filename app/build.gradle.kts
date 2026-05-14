plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "gs.ad.gsadsexample"
    compileSdk = 36

    defaultConfig {
        applicationId = "anime.girlfriend.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("com.google.android.gms:play-services-ads:25.2.0")
    implementation ("com.google.android.ump:user-messaging-platform:4.0.0")

    //lifecycle + multidex
    implementation ("androidx.multidex:multidex:2.0.1")
    implementation ("com.github.eriffanani:ContentLoader:1.2.0")
    implementation ("com.facebook.shimmer:shimmer:0.5.0@aar")
    implementation ("androidx.lifecycle:lifecycle-process:2.10.0")
//    implementation ("com.github.caprinixdev:ultra_a_d:1.0.15")

    implementation (project(":gsadutils"))
}
