plugins {
    id("com.android.application")
}

android {
    namespace = "com.wearsleepmodedisabler"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wearsleepmodedisabler"
        minSdk = 30
        // Target 35+ confines DND changes to the app's own rule, not Samsung's global DND.
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
