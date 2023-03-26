package app.passwordstore.gradle.crowdin.api

import com.squareup.moshi.Json

data class ListProjects(@Json(name = "data") val projects: List<ProjectData>)

data class ProjectData(@Json(name = "data") val project: Project)

data class Project(val id: Long, val identifier: String)
