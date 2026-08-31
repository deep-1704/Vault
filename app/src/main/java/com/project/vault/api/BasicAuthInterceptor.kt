package com.project.vault.api

import com.project.vault.security.AuthSessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that automatically injects the HTTP Basic Auth header
 * for authenticated requests if credentials exist in [AuthSessionManager].
 */
@Singleton
class BasicAuthInterceptor @Inject constructor(
    private val authSessionManager: AuthSessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If the request already specifies an Authorization header (e.g. login call), preserve it
        if (originalRequest.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(originalRequest)
        }

        val authHeader = authSessionManager.getBasicAuthHeader()
        val request = if (authHeader != null) {
            originalRequest.newBuilder()
                .header(HEADER_AUTHORIZATION, authHeader)
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}
