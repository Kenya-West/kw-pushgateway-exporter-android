package com.kw.pushgatewayexporter.tls

import android.content.Intent
import android.security.KeyChain
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Helpers for diagnosing and remediating TLS trust-anchor errors on older
 * Android devices whose system store predates ISRG Root X1 (Let's Encrypt).
 *
 * Affects Android < 7.1.1 (API < 25) — those devices do not ship ISRG Root X1,
 * so any server whose chain terminates at it (most Let's Encrypt sites) fails
 * with CertPathValidatorException: "Trust anchor for certification path not found".
 */
object TlsErrorHelper {

    // Published SHA-256 fingerprint of ISRG Root X1 (self-signed root).
    // https://letsencrypt.org/certificates/
    private const val ISRG_ROOT_X1_SHA256 =
        "96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6"

    // DER-encoded root cert. letsencrypt.org itself chains to ISRG Root X1,
    // so on an affected device fetching this URL with the system trust store
    // would itself fail — we use a trust-all client and verify the payload by
    // SHA-256 fingerprint instead. The fingerprint is the security boundary.
    private const val ISRG_ROOT_X1_URL =
        "https://letsencrypt.org/certs/isrgrootx1.der"

    private const val CERT_NAME = "ISRG Root X1 (Let's Encrypt)"

    /** True if the error message looks like a missing-root-CA failure. */
    fun isTrustAnchorError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return "trust anchor" in m ||
            "certpathvalidatorexception" in m ||
            "certificate_unknown" in m ||
            ("ssl" in m && "unable to find valid certification path" in m)
    }

    /**
     * Download the ISRG Root X1 DER and verify its fingerprint.
     * Throws on network or fingerprint mismatch.
     */
    fun downloadIsrgRootX1(): ByteArray {
        val client = trustAllClient()
        val request = Request.Builder().url(ISRG_ROOT_X1_URL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} fetching root cert")
            }
            val bytes = response.body?.bytes()
                ?: throw java.io.IOException("Empty response fetching root cert")
            val fp = sha256Hex(bytes)
            if (!fp.equals(ISRG_ROOT_X1_SHA256, ignoreCase = true)) {
                throw java.security.GeneralSecurityException(
                    "Fingerprint mismatch: got $fp, expected $ISRG_ROOT_X1_SHA256"
                )
            }
            return bytes
        }
    }

    /**
     * Build an Intent that launches the system certificate installer.
     * Caller must startActivity() from an Activity context.
     */
    fun buildInstallIntent(certDerBytes: ByteArray): Intent {
        return KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, certDerBytes)
            putExtra(KeyChain.EXTRA_NAME, CERT_NAME)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun trustAllClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .sslSocketFactory(ctx.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
