import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lstepnio.egauge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lstepnio.egauge"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val uploadStore = providers.environmentVariable("EGAUGE_UPLOAD_STORE_FILE").orNull
    val uploadStorePassword = providers.environmentVariable("EGAUGE_UPLOAD_STORE_PASSWORD").orNull
    val uploadKeyAlias = providers.environmentVariable("EGAUGE_UPLOAD_KEY_ALIAS").orNull
    val uploadKeyPassword = providers.environmentVariable("EGAUGE_UPLOAD_KEY_PASSWORD").orNull
    val uploadCredentials = listOf(uploadStore, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
    require(uploadCredentials.all { it == null } || uploadCredentials.all { !it.isNullOrBlank() }) {
        "Set all four EGAUGE_UPLOAD_* environment variables to sign a release"
    }
    if (uploadCredentials.all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("upload") {
                storeFile = File(uploadStore!!)
                storePassword = uploadStorePassword!!
                keyAlias = uploadKeyAlias!!
                keyPassword = uploadKeyPassword!!
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("upload")
    }

    buildFeatures { compose = true }
    sourceSets.getByName("test").resources.srcDir("../../contracts/parity")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
