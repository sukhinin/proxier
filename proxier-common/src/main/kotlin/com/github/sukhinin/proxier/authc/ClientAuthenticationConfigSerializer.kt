package com.github.sukhinin.proxier.authc

import com.github.sukhinin.proxier.http.HttpUtils

object ClientAuthenticationConfigSerializer {

    const val MIME_TYPE = "application/x-www-form-urlencoded"

    private const val OIDC_CLIENT_ID_FIELD = "oidc.client.id"
    private const val OIDC_CLIENT_SCOPE_FIELD = "oidc.client.scope"
    private const val OIDC_AUTHORIZATION_ENDPOINT_FIELD = "oidc.endpoints.authorization"
    private const val OIDC_TOKEN_ENDPOINT_FIELD = "oidc.endpoints.token"

    fun serialize(config: ClientAuthenticationConfig): String {
        val map = HashMap<String, String>()
        map[OIDC_CLIENT_ID_FIELD] = config.clientId
        map[OIDC_CLIENT_SCOPE_FIELD] = config.clientScope
        map[OIDC_AUTHORIZATION_ENDPOINT_FIELD] = config.authorizationEndpoint
        map[OIDC_TOKEN_ENDPOINT_FIELD] = config.tokenEndpoint
        return HttpUtils.formEncode(map)
    }

    fun deserialize(data: String): ClientAuthenticationConfig {
        val map = HttpUtils.formDecode(data)
        return ClientAuthenticationConfig(
            clientId = map.getValue(OIDC_CLIENT_ID_FIELD),
            clientScope = map.getValue(OIDC_CLIENT_SCOPE_FIELD),
            authorizationEndpoint = map.getValue(OIDC_AUTHORIZATION_ENDPOINT_FIELD),
            tokenEndpoint = map.getValue(OIDC_TOKEN_ENDPOINT_FIELD),
        )
    }
}
