package com.github.exidcuter.dockerregistryexplorerplugin.services

import com.intellij.openapi.project.Project
import com.github.exidcuter.dockerregistryexplorerplugin.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
