plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test {
    System.getProperty("jdk.net.unixdomain.tmpdir")?.let { systemProperty("jdk.net.unixdomain.tmpdir", it) }
}
