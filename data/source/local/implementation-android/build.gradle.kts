plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
}

dependencies {
    api(project(":data:source:local:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
    implementation(libs.androidx.room)
    ksp(libs.androidx.room.codegen)
}

android {
    val targetSdkVersion = System.getProperty("TARGET_SDK_VERSION").toInt()
    compileSdk = targetSdkVersion
    defaultConfig.minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
    kotlinOptions.jvmTarget = libs.versions.jvm.target.get()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    namespace = "com.pandulapeter.campfire.data.source.local.implementationAndroid"
}