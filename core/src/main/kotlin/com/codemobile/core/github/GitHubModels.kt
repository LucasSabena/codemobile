package com.codemobile.core.github

data class GitHubUser(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null
)

data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val cloneUrl: String,
    val description: String? = null,
    val isPrivate: Boolean = false,
    val updatedAt: String? = null
)
