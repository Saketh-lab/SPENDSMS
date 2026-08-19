package com.spendsms.app.data.parser

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies ECDSA P-256 (SHA256withECDSA) signatures over declarative rules UTF-8 bytes.
 *
 * Private keys never ship in the app; only the SPKI public key is embedded.
 */
@Singleton
class ParserBundleSignatureVerifier @Inject constructor(
    private val publicKeyProvider: ParserSigningPublicKeyProvider,
) {

    fun verify(rulesUtf8: String, signatureBase64: String): SignatureOutcome {
        val signatureBytes = try {
            Base64.getDecoder().decode(signatureBase64.trim())
        } catch (_: IllegalArgumentException) {
            return SignatureOutcome.Invalid("signatureBase64 is not valid Base64")
        }
        if (signatureBytes.isEmpty()) {
            return SignatureOutcome.Invalid("signature is empty")
        }

        return try {
            val publicKey = publicKeyProvider.publicKey()
            val signature = Signature.getInstance(ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(rulesUtf8.toByteArray(Charsets.UTF_8))
            if (signature.verify(signatureBytes)) {
                SignatureOutcome.Valid
            } else {
                SignatureOutcome.Invalid("ECDSA signature verification failed")
            }
        } catch (e: Exception) {
            SignatureOutcome.Invalid("Signature verification error: ${e::class.java.simpleName}")
        }
    }

    sealed class SignatureOutcome {
        data object Valid : SignatureOutcome()
        data class Invalid(val detail: String) : SignatureOutcome()
    }

    companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}

fun interface ParserSigningPublicKeyProvider {
    fun publicKey(): PublicKey
}

@Singleton
class AssetParserSigningPublicKeyProvider @Inject constructor(
    private val assetLoader: ParserAssetLoader,
) : ParserSigningPublicKeyProvider {

    @Volatile
    private var cached: PublicKey? = null

    override fun publicKey(): PublicKey {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val b64 = assetLoader.readText(PUBLIC_KEY_ASSET).trim()
            val der = Base64.getDecoder().decode(b64)
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(der))
            cached = key
            return key
        }
    }

    companion object {
        const val PUBLIC_KEY_ASSET = "parser/signing_public_key_spki.b64"
    }
}
