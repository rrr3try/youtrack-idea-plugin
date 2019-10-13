package com.github.jk1.ytplugin.rest

import com.github.jk1.ytplugin.issues.model.Issue
import com.github.jk1.ytplugin.tasks.YouTrackServer
import com.google.gson.JsonParser
import org.apache.commons.httpclient.methods.GetMethod
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.http.entity.StringEntity
import org.jdom.input.SAXBuilder
import java.io.InputStreamReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fetches YouTrack issues with issue description formatted from wiki into html on server side.
 */
class IssuesRestClient(override val repository: YouTrackServer) : RestClientTrait {

    fun createDraft(summary: String): String {
        val encodedSummary = URLEncoder.encode(summary, StandardCharsets.UTF_8.name())
        val method = PostMethod("${repository.url}/api/admin/users/me/drafts")
        //todo: force markdown
        method.requestEntity = StringRequestEntity("""{"summary": "$encodedSummary"}""", "application/json", StandardCharsets.UTF_8.name())
        return method.connect {
            val status = httpClient.executeMethod(method)
            if (status == 200) {
                val stream = InputStreamReader(method.responseBodyAsLoggedStream(), "UTF-8")
                JsonParser().parse(stream).asJsonObject.get("id").asString
            } else {
                throw RuntimeException(method.responseBodyAsLoggedString())
            }
        }
    }

    fun getIssue(id: String): Issue? {
        val method = GetMethod("${repository.url}/rest/issue/$id?wikifyDescription=true")
        return method.execute { IssueJsonParser.parseIssue(it, repository.url) }
    }

    fun getIssues(query: String = ""): List<Issue> {
        // todo: customizable "max" limit
        val url = "${repository.url}/rest/issue?filter=${query.urlencoded}&max=30&useImplicitSort=true&wikifyDescription=true"
        val issues = GetMethod(url).execute {
            it.asJsonObject.getAsJsonArray("issue").mapNotNull { IssueJsonParser.parseIssue(it, repository.url) }
        }
        if (issues.any { it.wikified }) {
            // this is YouTrack 2018.1+, so we can return wikified issues right away
            return issues
        } else {
            /*
             * There's no direct API to get formatted issues by a search query, so two-stage fetch is used:
             * - Fetch issues by search query and select all projects these issues belong to
             * - For each project request formatted issues with an auxiliary search request like
             * "issue id: PR-1 or issue id: PR-2 or ...". Auxiliary source request is necessary
             *  due to https://github.com/jk1/youtrack-idea-plugin/issues/30
             * - Sort issues to match the order from the first request
             */
            val issuesIds = issues.map { it.id }
            val projects = issuesIds.groupBy { it.split("-")[0] }
            val wikifiedIssues = projects.flatMap {
                val issueIdsQuery = it.value.joinToString(" ") { "#$it" }
                getWikifiedIssuesInProject(it.key, issueIdsQuery)
            }
            return issuesIds.mapNotNull { id -> wikifiedIssues.firstOrNull { issue -> id == issue.id } }
        }
    }

    private fun getWikifiedIssuesInProject(projectShortName: String, query: String = ""): List<Issue> {
        val url = "${repository.url}/rest/issue/byproject/${projectShortName.urlencoded}"
        val params = "filter=${query.urlencoded}&wikifyDescription=true&max=30"
        val method = GetMethod("$url?$params")
        return method.execute { it.asJsonArray.mapNotNull { IssueJsonParser.parseIssue(it, repository.url) } }
    }
}