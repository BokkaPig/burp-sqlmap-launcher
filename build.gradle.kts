plugins {
    id("java")
}

group = "uk.co.periculo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.7")

    // The API is provided by Burp at runtime, but tests stub it, so they need it
    // on their own classpath.
    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// Burp ships its own JRE (Java 26 at time of writing); targeting 17 keeps the
// jar loadable on older Burp builds while compiling with whatever JDK is here.
//
// The output is ordinary Java bytecode, so one jar runs on every architecture.
// There is no amd64 or arm64 build to choose between.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

// Gradle compiles with the JVM it is itself running on, so that JVM has to be a
// JDK. When it is a JRE, Gradle's own error ("does not provide the required
// capabilities: [JAVA_COMPILER]") says nothing about how to fix it, and the fix
// differs per distribution and architecture. Say it plainly instead.
gradle.taskGraph.whenReady {
    if (allTasks.none { it is JavaCompile }) return@whenReady

    val gradleJvm = File(System.getProperty("java.home"))
    val hasCompiler = File(gradleJvm, "bin/javac").exists() || File(gradleJvm, "bin/javac.exe").exists()
    if (hasCompiler) return@whenReady

    throw GradleException(
        """
        |This build needs a JDK, but Gradle is running on a JRE with no compiler.
        |  Gradle's JVM: $gradleJvm
        |
        |Any of these fixes it:
        |  ./build.sh                     finds a JDK for you, on any architecture
        |  export JAVA_HOME=/path/to/jdk  a directory containing bin/javac
        |
        |No JDK installed? Install one (17 or newer):
        |  Debian / Kali / Ubuntu   sudo apt install default-jdk
        |  Fedora / RHEL            sudo dnf install java-21-openjdk-devel
        |  Arch                     sudo pacman -S jdk-openjdk
        |  macOS (Homebrew)         brew install openjdk@21
        |
        |Do not copy a JAVA_HOME path out of the docs: the directory name differs
        |between distributions and architectures.
        """.trimMargin()
    )
}

tasks.jar {
    archiveBaseName = "burp-sqlmap-launcher"
    archiveVersion = ""
    destinationDirectory = layout.projectDirectory.dir("build")
    manifest {
        attributes("Implementation-Title" to "sqlmap Launcher", "Implementation-Version" to project.version)
    }

    // Build the jar reproducibly: dropping timestamps and fixing entry order
    // makes it byte-identical wherever it is built, which is what lets CI prove
    // the amd64 and arm64 builds really do produce the same artefact.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.build {
    doLast {
        println("Extension jar: ${layout.projectDirectory.dir("build").file("burp-sqlmap-launcher.jar").asFile}")
    }
}
