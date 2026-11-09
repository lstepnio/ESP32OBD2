import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val playBillingPublicKey = providers.gradleProperty("egaugePlayBillingPublicKey").orNull.orEmpty()
if (playBillingPublicKey.isNotEmpty()) {
    require(playBillingPublicKey.length <= 1_024)
    KeyFactory.getInstance("RSA").generatePublic(
        X509EncodedKeySpec(Base64.getDecoder().decode(playBillingPublicKey))
    )
}

android {
    namespace = "com.lstepnio.egauge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lstepnio.egauge"
        minSdk = 29
        targetSdk = 36
        versionCode = 58
        versionName = "0.2.0-dev.58"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PRIVATE_OTA_PROVISIONED", "false")
        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$playBillingPublicKey\"")
    }

    providers.gradleProperty("egaugePrivateOtaAssets").orNull?.let { path ->
        val directory = File(path)
        val assets = directory.listFiles().orEmpty()
        require(
            directory.isAbsolute && assets.map { it.name }.toSet() ==
                setOf("private-ota-target.json", "private-ota-public.pem") &&
                assets.all { it.isFile && it.length() in 1..4096 }
        ) { "Private OTA requires an absolute directory containing only its two provisioning assets" }
        val pem = directory.resolve("private-ota-public.pem").readLines().filter { it.isNotBlank() }
        require(pem.firstOrNull() == "-----BEGIN PUBLIC KEY-----" && pem.lastOrNull() == "-----END PUBLIC KEY-----")
        val encodedKey = Base64.getDecoder().decode(pem.drop(1).dropLast(1).joinToString(""))
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encodedKey))
        sourceSets.getByName("debug").assets.srcDir(directory)
        buildTypes.getByName("debug").buildConfigField("boolean", "PRIVATE_OTA_PROVISIONED", "true")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all { testTask ->
            if (testTask.name == "testReleaseUnitTest") {
                testTask.useJUnit {
                    excludeCategories("com.lstepnio.egauge.DevelopmentConfiguration")
                }
            }
        }
        managedDevices {
            localDevices {
                create("ciPhone") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "google"
                    require64Bit = true
                }
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:designsystem"))
    implementation("com.android.billingclient:billing:9.1.0")
    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("Play Billing brings an older Fragment transitively, which is incompatible with Activity Result")
        }
    }
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.window:window:1.4.0")
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
