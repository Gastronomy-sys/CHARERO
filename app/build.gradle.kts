plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.evidock.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "io.evidock.app.pleosdev"
        minSdk = 33
        targetSdk = 34
        versionCode = 8
        versionName = "0.4-charero-navi-candidate"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}


// CHARERO_NAVI_V2: compile actual NaviHelper when using the official Pleos SDK.
val chareroNavi = providers.gradleProperty("chareroNaviSdk").orNull != "false"
android.sourceSets.getByName("main").java.srcDir(
    if (chareroNavi) "src/naviSdk/java" else "src/naviStub/java"
)
if (chareroNavi) {
    dependencies { implementation("ai.pleos.playground:NaviHelper:3.0.5") }
}
