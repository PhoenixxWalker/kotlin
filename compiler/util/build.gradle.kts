
apply { plugin("kotlin") }

jvmTarget = "1.6"

configureIntellijPlugin {
    setExtraDependencies("intellij-core", "jps-standalone")
}

dependencies {
    compile(project(":core"))
}

afterEvaluate {
    dependencies {
        compile(intellijCoreJar())
        compile(intellijCoreJarDependencies())
        compile(intellijExtra("jps-standalone") { include("jps-model.jar") })
    }
}

sourceSets {
    "main" {
        projectDefault()
        resources.srcDir(File(rootDir, "resources")).apply { include("**") }
    }
    "test" {}
}

