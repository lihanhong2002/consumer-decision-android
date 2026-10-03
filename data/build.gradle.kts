plugins { id("com.android.library"); kotlin("plugin.serialization"); id("com.google.devtools.ksp") }
android { namespace = "com.sixmodel.consumerdecision.data"; compileSdk = 36; defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    api("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
