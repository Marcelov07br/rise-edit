plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test {
    systemProperty("perf", System.getProperty("perf") ?: "false")
    System.getProperty("perf.out")?.let { systemProperty("perf.out", it) }
    outputs.upToDateWhen { System.getProperty("perf") != "true" }
    System.getProperty("jdk.net.unixdomain.tmpdir")?.let {
        systemProperty("jdk.net.unixdomain.tmpdir", it)
    }
}
