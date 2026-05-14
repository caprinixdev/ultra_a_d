plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "gs.ad.utils"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("com.google.android.gms:play-services-ads:25.2.0")
    implementation ("com.google.android.ump:user-messaging-platform:4.0.0")

    //lifecycle + multidex
    implementation ("androidx.lifecycle:lifecycle-process:2.10.0")
    implementation ("com.github.eriffanani:ContentLoader:1.2.0")
    implementation ("com.android.billingclient:billing:8.3.0")
    implementation ("com.google.guava:guava:33.6.0-jre")

    implementation("com.facebook.shimmer:shimmer:0.5.0@aar")
}

publishing{
    publications{
        register<MavenPublication>("release"){
            afterEvaluate{
                from(components["release"])
            }
        }
    }
}