import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("eclipse")
}

val buildTypeRelease = "release"
android {
    compileSdkVersion(28)

    defaultConfig {
        applicationId = "com.zeapo.pwdstore"
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode = 10302
        versionName = "1.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        setSourceCompatibility(JavaVersion.VERSION_1_8)
        setTargetCompatibility(JavaVersion.VERSION_1_8)
    }

    lintOptions {
        isAbortOnError = true // make sure build fails with lint errors!
        disable("MissingTranslation", "PluralsCandidate")
    }

    packagingOptions {
        exclude(".readme")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE.txt")
    }

    /*
     * To sign release builds, create the file `gradle.properties` in
     * $HOME/.gradle or in your project directory with this content:
     *
     * mStoreFile=/path/to/key.store
     * mStorePassword=xxx
     * mKeyAlias=alias
     * mKeyPassword=xxx
     */
    if (project.hasProperty("mStoreFile") &&
            project.hasProperty("mStorePassword") &&
            project.hasProperty("mKeyAlias") &&
            project.hasProperty("mKeyPassword")) {
        signingConfigs {
            getByName(buildTypeRelease) {
                storeFile = file(project.properties["mStoreFile"] as String)
                storePassword = project.properties["mStorePassword"] as String
                keyAlias = project.properties["mKeyAlias"] as String
                keyPassword = project.properties["mKeyPassword"] as String
            }
        }
        buildTypes.getByName(buildTypeRelease).signingConfig = signingConfigs.getByName(buildTypeRelease)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.0.0")
    implementation("androidx.annotation:annotation:1.0.0")
    implementation("org.sufficientlysecure:openpgp-api:11.0")
    implementation("com.nononsenseapps:filepicker:2.4.2")
    implementation("org.eclipse.jgit:org.eclipse.jgit:3.7.1.201504261725-r") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("com.jcraft:jsch:0.1.54")
    implementation("commons-io:commons-io:2.5")
    implementation("commons-codec:commons-codec:1.11")
    implementation("com.jayway.android.robotium:robotium-solo:5.3.1")
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")

    // Testing-only dependencies
    androidTestImplementation("junit:junit:4.12")
    androidTestImplementation("org.mockito:mockito-core:2.18.0")
    androidTestImplementation("androidx.test:runner:1.1.0-alpha4")
    androidTestImplementation("androidx.test:rules:1.1.0-alpha4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0-alpha4")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.1.0-alpha4")
}
