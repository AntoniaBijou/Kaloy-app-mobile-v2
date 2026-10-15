plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // Lit androidApp/google-services.json et en genere les ressources Android.
    // Absent, ce fichier fait echouer le build du module application : il n est
    // pas dans le depot, chacun telecharge le sien depuis la console Firebase.
    alias(libs.plugins.googleServices) apply false
}