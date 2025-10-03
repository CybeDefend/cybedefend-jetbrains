plugins {
    java
    kotlin("jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    kotlin("plugin.serialization") version "1.9.25"
}

group = "com.cybedefend"

version = "0.4.1"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    // --- IntelliJ ---
    intellijPlatform { create("IC", "2024.2.6") }

    // --- runtime libs ---
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // <-- add
    implementation("com.google.code.gson:gson:2.11.0")

    // --- KotlinX (si vraiment nécessaire) ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.1")
}

configurations.all {
    resolutionStrategy.force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.25",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.25",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.25"
    )
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242" // 2024.2
        }

        changeNotes = """
          <h3>0.4.0</h3>
          <ul>
            <li><b>Region Switch (US/EU)</b>: selectable in Settings, applied live per workspace.</li>
            <li><b>Reset & Reconfigure</b>: reset credentials and trigger the full configuration workflow.</li>
            <li>Dynamic Retrofit base URL + ChatBot SSE aligned with selected region.</li>
            <li>API key / projectId preflight checks for clearer errors.</li>
          </ul>
          <p>See <code>CHANGELOG.md</code> for full details.</p>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            // ======================================================================
            // --- Groupe 1 : Famille 2025.2 (résolution automatique du patch) ---
            // ======================================================================

            // --- Core IDEs ---
            create("IC", "2025.2")  // IntelliJ IDEA Community
            create("PS", "2025.2")  // PhpStorm
            create("WS", "2025.2")  // WebStorm
            create("PC", "2025.2")  // PyCharm Professional
            create("RM", "2025.2")  // RubyMine
            create("GO", "2025.2")  // GoLand

            // --- IDEs .NET & C/C++/Rust ---
            create("RD", "2025.2")
            create("CL", "2025.2")  // CLion
            create("RR", "2025.2")  // RustRover

            // --- Data & QA ---
            create("DB", "2025.2")  // DataGrip

            // ======================================================================
            // --- Groupe 2 : Famille 2024.3 (résolution automatique du patch) ---
            // ======================================================================

            // --- Core IDEs ---
            create("IC", "2024.3")  // IntelliJ IDEA Community
            create("PS", "2024.3")  // PhpStorm
            create("WS", "2024.3")  // WebStorm
            create("PC", "2024.3")  // PyCharm Professional
            create("RM", "2024.3")  // RubyMine
            create("GO", "2024.3")  // GoLand

            // --- IDEs .NET & C/C++/Rust ---
            create("RD", "2024.3")
            create("CL", "2024.3")  // CLion
            create("RR", "2024.3")  // RustRover

            // --- Data & QA ---
            create("DB", "2024.3")  // DataGrip
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions.jvmTarget = "21" }
}