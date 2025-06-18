// Copyright 2023-2024 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.parcelo.console.routes.auth

import app.accrescent.parcelo.console.data.Reviewer
import app.accrescent.parcelo.console.data.Reviewers
import app.accrescent.parcelo.console.data.Session
import app.accrescent.parcelo.console.data.User
import app.accrescent.parcelo.console.data.Users
import app.accrescent.parcelo.console.data.WhitelistedGitHubUser
import app.accrescent.parcelo.console.data.WhitelistedGitHubUsers
import io.ktor.client.HttpClient
import io.ktor.http.Cookie
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.call
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.oauth
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.sessions.generateSessionId
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.kohsuke.github.GitHubBuilder

/**
 * The name of the OAuth2 state cookie for production mode
 */
const val COOKIE_OAUTH_STATE_PROD = "__Host-oauth-state"

/**
 * The name of the OAuth2 state cookie for development mode
 */
const val COOKIE_OAUTH_STATE_DEVEL = "oauth-state"

/**
 * The duration of the OAuth2 state cookie's lifetime
 */
const val COOKIE_OAUTH_STATE_LIFETIME = 60 * 60 // 1 hour

/**
 * A helper method for getting the name of the OAuth2 state cookie name in the current mode
 */
val ApplicationEnvironment.oauthStateCookieName
    get() =
        if (!developmentMode) COOKIE_OAUTH_STATE_PROD else COOKIE_OAUTH_STATE_DEVEL

/**
 * The result of successful authentication
 *
 * @property reviewer whether the logged-in user has the reviewer role
 * @property publisher whether the logged-in user has the publisher role
 */
@Serializable
data class AuthResult(val reviewer: Boolean, val publisher: Boolean)

/**
 * Registers GitHub OAuth2 authentication configuration
 */
fun AuthenticationConfig.github(
    clientId: String,
    clientSecret: String,
    redirectUrl: String,
    httpClient: HttpClient = HttpClient(),
) {
	println("=== GitHub OAuth Configuration ===")
    println("Client ID: $clientId")
    println("Client Secret: ${clientSecret.take(5)}...")  // 只打印前5个字符，保护敏感信息
    println("Redirect URL: $redirectUrl")
    oauth("oauth2-github") {
        urlProvider = { 
		 println("=== Providing OAuth URL: $redirectUrl ===")
		redirectUrl
	}
        providerLookup = {
		 println("=== Looking up OAuth provider ===")
            OAuthServerSettings.OAuth2ServerSettings(
                name = "github",
                authorizeUrl = "https://github.com/login/oauth/authorize",
                accessTokenUrl = "https://github.com/login/oauth/access_token",
                requestMethod = HttpMethod.Post,
                clientId = clientId,
                clientSecret = clientSecret,
                defaultScopes = listOf("user:email"),
                onStateCreated = { call, state ->
                    // Cross-site request forgery (CSRF) protection.
                    // See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-30#section-10.12
          println("=== OAuth State Created ===")
                    println("State: $state")
                    println("Cookie Name: ${call.application.environment.oauthStateCookieName}")
		    call.response.cookies.append(
                        Cookie(
                            name = call.application.environment.oauthStateCookieName,
                            value = state,
                            maxAge = COOKIE_OAUTH_STATE_LIFETIME,
                            path = "/",
                            secure = true,
                            httpOnly = true,
                            extensions = mapOf("SameSite" to "Lax")
                        )
                    )
                }
            )
        }
        client = httpClient
    }
}

/**
 * Registers all GitHub authentication routes
 */
fun Route.githubRoutes() {
    authenticate("oauth2-github") {
        route("/github") {
            get("/login") {
                println("=== Handling GitHub login request ===")
	    }

            get("/callback2") {
		     println("=== Handling GitHub callback2 ===${call.request.local.uri},Query Parameters:${call.request.queryParameters},Headers:${call.request.headers}")

                // Cross-site request forgery (CSRF) protection.
                // See https://datatracker.ietf.org/doc/html/draft-ietf-oauth-v2-30#section-10.12
                val oauthCookie =
                    call.request.cookies[call.application.environment.oauthStateCookieName]
                if (oauthCookie == null) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

                val principal: OAuthAccessTokenResponse.OAuth2 = call.principal() ?: return@get
                if (principal.state != oauthCookie) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }

                val githubUser = GitHubBuilder().withOAuthToken(principal.accessToken).build()

                val githubUserId = githubUser.myself.id

                // Register if not already registered
                val user = transaction {
                    User.find { Users.githubUserId eq githubUserId }.firstOrNull()
                } ?: run {
                    val email = githubUser.myself
                        .listEmails()
                        .find { it.isPrimary && it.isVerified }
                        ?.email
                        ?: run {
				println("email check forbidden")
                            call.respond(HttpStatusCode.Forbidden)
                            return@get
                        }

                    transaction {
			   println("create new User")
                        User.new {
                            this.githubUserId = githubUserId
                            this.email = email
                        }
                    }
                }
		println("current GitHub ID:${user.githubUserId}")
		println("DEBUG_USER_GITHUB_ID:${System.getenv("DEBUG_USER_GITHUB_ID")}")
/*
                val userNotWhitelisted = transaction {
                    WhitelistedGitHubUser
                        .find { WhitelistedGitHubUsers.id eq user.githubUserId }
                        .empty()
                }
                if (userNotWhitelisted) {
			println("userNotWhitelisted")
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
*/
                val sessionId = transaction {
                    Session.new(generateSessionId()) {
			    println("new session");
                        userId = user.id
                        expiryTime =
                            System.currentTimeMillis() + SESSION_LIFETIME.inWholeMilliseconds
                    }.id.value
                }
		println("before call.sessions.set")
                call.sessions.set(Session(sessionId))

                // Determine whether the user is a reviewer
                val reviewer = transaction {
                    Reviewer.find { Reviewers.userId eq user.id }.singleOrNull()
                } != null
		println("=== Authentication successful for user: $githubUserId ===")
                call.respond(HttpStatusCode.OK, AuthResult(reviewer, user.publisher))
            }
        }
    }
}
