
apply { plugin("kotlin") }

configureIntellijPlugin()

dependencies {
    compile(project(":idea"))
    compile(project(":compiler:light-classes"))
    compile(project(":compiler:frontend.java"))

    compile(ideaPluginDeps("idea-junit", plugin = "junit"))
    compile(ideaPluginDeps("testng", "testng-plugin", plugin = "testng"))

    compile(ideaPluginDeps("coverage", plugin = "coverage"))

    compile(ideaPluginDeps("java-decompiler", plugin = "java-decompiler"))
}

afterEvaluate {
    dependencies {
        compileOnly(intellij { include("openapi.jar", "idea.jar") })
    }
}


sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}
