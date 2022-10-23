package me.him188.indexserver.routing

import io.ktor.http.*
import io.ktor.server.html.*
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br

object StaticContents {
    val Welcome = HtmlContent(HttpStatusCode.OK) {
        body {
            text("This is Index Server.")
            br()
            a(href = "https://github.com/him188/build-index-server") {
                text("Visit Source code")
            }
        }
    }
}