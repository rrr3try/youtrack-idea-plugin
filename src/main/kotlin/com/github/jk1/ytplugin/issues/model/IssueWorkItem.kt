package com.github.jk1.ytplugin.issues.model

import com.google.gson.JsonElement
import java.util.*


class IssueWorkItem(item: JsonElement) {

    val issueId: String = item.asJsonObject.get("issue").asJsonObject.get("idReadable").asString
    val created: Date =  Date(item.asJsonObject.get("created").asLong)
    val date: Date = Date(item.asJsonObject.get("date").asLong)
    val value: String = item.asJsonObject.get("duration").asJsonObject.get("presentation").asString
    val author: String = item.asJsonObject.get("author").asJsonObject.get("name").asString
    val id: String = item.asJsonObject.get("id").asString

    val comment: String? = if (!item.asJsonObject.get("text").isJsonNull)
        item.asJsonObject.get("text").asString
    else
        null

}