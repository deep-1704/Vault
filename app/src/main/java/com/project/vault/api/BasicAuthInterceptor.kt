package com.project.vault.api

import com.project.vault.security.AuthSessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that:
 *  1. Automatically injects the HTTP Basic Auth header for authenticated requests
 *     if credentials exist in [AuthSessionManager].
 *  2. Inspects every response and, if the server returns **401 Unauthorized**
 *     while a session is active, immediately clears the local session so the UI
 *     reacts via [AuthSessionManager.isLoggedIn] and returns to the logged-out state.
 */
@Singleton
class BasicAuthInterceptor @Inject constructor(
    private val authSessionManager: AuthSessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If the request already specifies an Authorization header (e.g. login call), preserve it
        val request = if (originalRequest.header(HEADER_AUTHORIZATION) != null) {
            originalRequest
        } else {
            val authHeader = authSessionManager.getBasicAuthHeader()
            if (authHeader != null) {
                originalRequest.newBuilder()
                    .header(HEADER_AUTHORIZATION, authHeader)
                    .build()
            } else {
                originalRequest
            }
        }

        val response = chain.proceed(request)

        // Auto-logout on 401: the server has rejected our credentials.
        // Only clear the session when one is actually active to avoid
        // interfering with the public signup/login flows.
        if (response.code == 401 && authSessionManager.hasValidSession()) {
            authSessionManager.clearSession()
        }

        return response
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
