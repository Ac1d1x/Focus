plugins {
    alias(libs.plugins.focuskit.android.library)
    alias(libs.plugins.focuskit.android.compose)
}

android {
    namespace = "fan.x0.focus.core.designsystem"

    androidResources {
        noCompress += listOf("ttf")
    }
}

dependencies {
    implementation(projects.core.error)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.compose.ui.tooling)
}
