package com.github.sukhinin.proxier.client.authc

import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import java.security.SecureRandom

@Suppress("UnstableApiUsage")
class ChallengeGenerator {

    private val secureRandom = SecureRandom.getInstanceStrong()
    private val secret = ByteArray(64).also(secureRandom::nextBytes)

    fun generateSeed(): ByteArray {
        return ByteArray(64).also(secureRandom::nextBytes)
    }

    fun generateVerifier(seed: ByteArray): String {
        val bytes = Hashing.hmacSha256(secret).hashBytes(seed).asBytes()
        return BaseEncoding.base64Url().encode(bytes).trimEnd('=')
    }

    fun generateChallenge(seed: ByteArray): String {
        val verifier = generateVerifier(seed)
        val verifierBytes = verifier.encodeToByteArray()
        val challenge = Hashing.sha256().hashBytes(verifierBytes).asBytes()
        return BaseEncoding.base64Url().encode(challenge).trimEnd('=')
    }

    fun getMethod(): String {
        return "S256"
    }
}
