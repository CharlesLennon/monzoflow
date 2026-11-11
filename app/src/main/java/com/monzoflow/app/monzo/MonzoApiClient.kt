package com.monzoflow.app.monzo

import com.monzoflow.app.data.model.*
import com.monzoflow.app.data.secure.SecureCredentialManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

class MonzoApiClient(private val credentialManager: SecureCredentialManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    Timber.d("KtorMonzo: $message")
                }
            }
        }
    }

    private val baseUrl = "https://api.monzo.com"

    private fun HttpRequestBuilder.authHeader() {
        val token = credentialManager.activeBearerToken
        if (token.isNotBlank()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    /**
     * Lists all Monzo accounts linked to the user's developer token.
     */
    suspend fun getAccounts(): Result<List<MonzoAccount>> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/accounts") {
                authHeader()
            }
            if (response.status.isSuccess()) {
                val accountsResp: MonzoAccountsResponse = response.body()
                Result.success(accountsResp.accounts.filter { !it.closed })
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Monzo accounts")
            Result.failure(e)
        }
    }

    /**
     * Queries the live balance & today's spending for an account.
     */
    suspend fun getBalance(accountId: String): Result<MonzoBalance> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/balance") {
                authHeader()
                parameter("account_id", accountId)
            }
            if (response.status.isSuccess()) {
                val balance: MonzoBalance = response.body()
                Result.success(balance)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Monzo balance")
            Result.failure(e)
        }
    }

    /**
     * Queries all user savings pots.
     */
    suspend fun getPots(): Result<List<MonzoPot>> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/pots") {
                authHeader()
            }
            if (response.status.isSuccess()) {
                val potsResp: MonzoPotsResponse = response.body()
                Result.success(potsResp.pots.filter { !it.deleted })
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching Monzo pots")
            Result.failure(e)
        }
    }

    /**
     * Pushes a rich interactive card into the user's official Monzo app feed.
     */
    suspend fun pushFeedItem(
        accountId: String,
        title: String,
        body: String,
        imageUrl: String = "",
        targetUrl: String = "",
        backgroundColor: String = "#FF4D4D"
    ): Result<Boolean> {
        return try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/feed",
                formParameters = Parameters.build {
                    append("account_id", accountId)
                    append("type", "basic")
                    append("params[title]", title)
                    append("params[body]", body)
                    if (imageUrl.isNotBlank()) {
                        append("params[image_url]", imageUrl)
                    }
                    if (targetUrl.isNotBlank()) {
                        append("params[url]", targetUrl)
                    }
                    if (backgroundColor.isNotBlank()) {
                        append("params[background_color]", backgroundColor)
                    }
                }
            ) {
                authHeader()
            }

            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error pushing Monzo feed item")
            Result.failure(e)
        }
    }

    /**
     * Annotates a transaction with notes, custom tags, and metadata.
     */
    suspend fun annotateTransaction(
        transactionId: String,
        notes: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<Boolean> {
        return try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/transactions/$transactionId",
                formParameters = Parameters.build {
                    if (notes.isNotBlank()) {
                        append("metadata[notes]", notes)
                    }
                    metadata.forEach { (k, v) ->
                        append("metadata[$k]", v)
                    }
                }
            ) {
                authHeader()
                method = HttpMethod.Patch
            }

            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error annotating Monzo transaction")
            Result.failure(e)
        }
    }

    /**
     * Moves money from the main account into a savings pot.
     */
    suspend fun depositToPot(
        potId: String,
        sourceAccountId: String,
        amountPence: Long,
        dedupeId: String = "monzoflow_${System.currentTimeMillis()}"
    ): Result<MonzoPot> {
        return try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/pots/$potId/deposit",
                formParameters = Parameters.build {
                    append("source_account_id", sourceAccountId)
                    append("amount", amountPence.toString())
                    append("dedupe_id", dedupeId)
                }
            ) {
                authHeader()
                method = HttpMethod.Put
            }

            if (response.status.isSuccess()) {
                val pot: MonzoPot = response.body()
                Result.success(pot)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error depositing to Monzo pot")
            Result.failure(e)
        }
    }

    /**
     * Withdraws money from a savings pot back to the main account.
     */
    suspend fun withdrawFromPot(
        potId: String,
        destinationAccountId: String,
        amountPence: Long,
        dedupeId: String = "monzoflow_${System.currentTimeMillis()}"
    ): Result<MonzoPot> {
        return try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/pots/$potId/withdraw",
                formParameters = Parameters.build {
                    append("destination_account_id", destinationAccountId)
                    append("amount", amountPence.toString())
                    append("dedupe_id", dedupeId)
                }
            ) {
                authHeader()
                method = HttpMethod.Put
            }

            if (response.status.isSuccess()) {
                val pot: MonzoPot = response.body()
                Result.success(pot)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error withdrawing from Monzo pot")
            Result.failure(e)
        }
    }

    /**
     * Automatically exchanges OAuth2 refresh_token for a fresh access_token.
     */
    suspend fun refreshOAuthToken(): Result<Boolean> {
        val clientId = credentialManager.oauthClientId
        val clientSecret = credentialManager.oauthClientSecret
        val refreshToken = credentialManager.oauthRefreshToken

        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            return Result.failure(IllegalStateException("OAuth2 credentials not configured"))
        }

        return try {
            val response: HttpResponse = httpClient.submitForm(
                url = "$baseUrl/oauth2/token",
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("refresh_token", refreshToken)
                }
            )

            if (response.status.isSuccess()) {
                val bodyText = response.bodyAsText()
                val parsed = json.parseToJsonElement(bodyText).jsonObject
                val newAccessToken = parsed["access_token"]?.jsonPrimitive?.content ?: ""
                val newRefreshToken = parsed["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
                val expiresInSeconds = parsed["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 21600L

                credentialManager.oauthAccessToken = newAccessToken
                credentialManager.oauthRefreshToken = newRefreshToken
                credentialManager.oauthTokenExpiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)

                Timber.i("Monzo OAuth token refreshed successfully, valid for ${expiresInSeconds}s")
                Result.success(true)
            } else {
                val err = response.bodyAsText()
                Result.failure(Exception("OAuth refresh failed HTTP ${response.status.value}: $err"))
            }
        } catch (e: Exception) {
            Timber.e(e, "OAuth token refresh exception")
            Result.failure(e)
        }
    }
}
