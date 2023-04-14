plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("cn.dorck.component.publisher") version "1.0.4"
}

publishOptions {
    group = "com.dorck.android"
    version = "0.1.0-LOCAL"
    artifactId = "trace-plugin"
}

gradlePlugin {
    plugins {
        create("MethodTracePlugin") {
            // Note: We need to make here id as same as module name, or
            // it will publish two different plugins.
            id = "cn.dorck.trace.plugin"
            implementationClass = "com.dorck.android.trace.plugin.MethodTracePlugin"
            displayName = "Method-Trace-Plugin"
            description = "A plugin for tracking the time spent on method calls."
        }
    }
}

dependencies {
    // Use gradle api
    implementation(gradleApi())
    compileOnly("com.android.tools.build:gradle:7.2.1")
    compileOnly(kotlin("stdlib"))
    compileOnly("org.ow2.asm:asm:9.1")
    compileOnly("org.ow2.asm:asm-commons:9.1")
    implementation("com.google.code.gson:gson:2.8.6")
}