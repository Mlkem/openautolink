package com.openautolink.app.transport.direct

import com.openautolink.app.diagnostics.OalLog
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * AA SSL/TLS handshake engine using Java SSLEngine.
 *
 * The AA protocol wraps TLS records inside AAP framing (channel 0, type 3).
 * This class handles the handshake by reading/writing AAP-framed TLS records,
 * then provides encrypt/decrypt for the post-handshake data stream.
 *
 * Uses Conscrypt if available (for TLS session resumption), falls back to
 * the platform TLS provider.
 *
 * The SSL certificate is a self-signed cert embedded in the app. The phone
 * doesn't verify it (AA uses SSL_VERIFY_NONE on the phone side).
 */
class AaSslEngine {

    companion object {
        private const val TAG = "AaSslEngine"
        private const val HANDSHAKE_TIMEOUT_MS = 15_000L

        // Singleton SSLContext — survives across AaSslEngine instances for TLS session
        // resumption. JSSE's ClientSessionContext caches sessions by (host, port);
        // reusing the same SSLContext + synthetic ("android-auto", 5277) key means
        // reconnects can resume the previous TLS session, saving 1-3 round trips.
        @Volatile
        private var sharedSslContext: SSLContext? = null
    }

    private lateinit var sslEngine: SSLEngine
    private lateinit var netOutBuffer: ByteBuffer   // TLS records to send
    private lateinit var appInBuffer: ByteBuffer     // Decrypted app data

    private val sslContext: SSLContext by lazy {
        synchronized(AaSslEngine::class.java) {
            sharedSslContext ?: createSslContextInternal().also { sharedSslContext = it }
        }
    }
    private val codec = AaWireCodec()

    /**
     * Perform the TLS handshake over the AA protocol.
     * Reads/writes AAP-framed TLS records on the given streams.
     * @return true if handshake succeeded
     */
    fun performHandshake(input: InputStream, output: OutputStream): Boolean {
        sslEngine = sslContext.createSSLEngine("android-auto", 5277).apply {
            useClientMode = true
        }
        val session = sslEngine.session
        netOutBuffer = ByteBuffer.allocateDirect(session.packetBufferSize)
        appInBuffer = ByteBuffer.allocateDirect(session.applicationBufferSize + 64)

        sslEngine.beginHandshake()

        var pendingTlsData = ByteArray(0)
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS

        while (true) {
            if (System.currentTimeMillis() >= deadline) {
                OalLog.e(TAG, "SSL handshake timed out")
                return false
            }

            when (sslEngine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.FINISHED,
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
                    OalLog.i(TAG, "SSL handshake complete")
                    netOutBuffer.clear()
                    appInBuffer.clear()
                    return true
                }

                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    // SSLEngine wants to produce TLS records → wrap and send
                    netOutBuffer.clear()
                    val result = sslEngine.wrap(arrayOf<ByteBuffer>(), netOutBuffer)
                    runDelegatedTasks()

                    if (result.status != SSLEngineResult.Status.OK &&
                        result.status != SSLEngineResult.Status.CLOSED) {
                        OalLog.e(TAG, "SSL wrap failed: ${result.status}")
                        return false
                    }

                    // Send TLS record wrapped in AAP frame (channel 0, type 3 = SSL_HANDSHAKE)
                    // Must use PLAIN flags (0x03) — encryption isn't established yet
                    val tlsBytes = ByteArray(result.bytesProduced())
                    netOutBuffer.flip()
                    netOutBuffer.get(tlsBytes)
                    val msg = AaMessage(AaChannel.CONTROL, 0x03,
                        AaMsgType.SSL_HANDSHAKE, tlsBytes)
                    codec.encode(msg, output)
                }

                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    // SSLEngine needs to consume TLS records
                    if (pendingTlsData.isEmpty()) {
                        // Read an AAP message containing a TLS record
                        val msg = codec.decode(input)
                        if (msg.channel != AaChannel.CONTROL || msg.type != AaMsgType.SSL_HANDSHAKE) {
                            OalLog.e(TAG, "Expected SSL_HANDSHAKE, got ${msg}")
                            return false
                        }
                        pendingTlsData = if (msg.payloadOffset == 0 && msg.payloadLength == msg.payload.size) {
                            msg.payload
                        } else {
                            msg.payload.copyOfRange(msg.payloadOffset, msg.payloadOffset + msg.payloadLength)
                        }
                    }

                    appInBuffer.clear()
                    val data = ByteBuffer.wrap(pendingTlsData)
                    val result = sslEngine.unwrap(data, appInBuffer)
                    runDelegatedTasks()

                    when (result.status) {
                        SSLEngineResult.Status.OK -> {
                            pendingTlsData = if (data.hasRemaining()) {
                                ByteArray(data.remaining()).also { data.get(it) }
                            } else ByteArray(0)
                        }
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            // Need more TLS data — read another AAP message and append
                            val msg = codec.decode(input)
                            if (msg.channel != AaChannel.CONTROL || msg.type != AaMsgType.SSL_HANDSHAKE) {
                                OalLog.e(TAG, "Expected SSL_HANDSHAKE during underflow, got ${msg}")
                                return false
                            }
                            val extra = if (msg.payloadOffset == 0 && msg.payloadLength == msg.payload.size) {
                                msg.payload
                            } else {
                                msg.payload.copyOfRange(msg.payloadOffset, msg.payloadOffset + msg.payloadLength)
                            }
                            pendingTlsData += extra
                        }
                        else -> {
                            OalLog.e(TAG, "SSL unwrap failed: ${result.status}")
                            return false
                        }
                    }
                }

                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    runDelegatedTasks()
                }

                else -> {
                    OalLog.e(TAG, "Unexpected handshake status: ${sslEngine.handshakeStatus}")
                    return false
                }
            }
        }
    }

    /**
     * Encrypt plaintext data (AA message payload) into a TLS record.
     * @return encrypted bytes, or null on failure
     */
    fun encrypt(data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray? {
        return try {
            val src = ByteBuffer.wrap(data, offset, length)
            netOutBuffer.clear()
            var result = sslEngine.wrap(src, netOutBuffer)
            runDelegatedTasks()
            if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                // Grow buffer to fit the TLS record
                val needed = sslEngine.session.packetBufferSize + length
                netOutBuffer = ByteBuffer.allocateDirect(needed)
                src.position(offset)
                result = sslEngine.wrap(src, netOutBuffer)
                runDelegatedTasks()
            }
            if (result.status != SSLEngineResult.Status.OK) {
                OalLog.e(TAG, "encrypt wrap failed: ${result.status}")
                return null
            }
            val out = ByteArray(result.bytesProduced())
            netOutBuffer.flip()
            netOutBuffer.get(out)
            out
        } catch (e: Exception) {
            OalLog.e(TAG, "encrypt error: ${e.message}")
            null
        }
    }

    /**
     * Decrypt a TLS record into plaintext AA message payload.
     * @return decrypted bytes, or null on failure
     */
    fun decrypt(data: ByteArray, offset: Int = 0, length: Int = data.size): ByteArray? {
        return try {
            val src = ByteBuffer.wrap(data, offset, length)
            appInBuffer.clear()
            var result = sslEngine.unwrap(src, appInBuffer)
            runDelegatedTasks()
            if (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                val needed = sslEngine.session.applicationBufferSize + length
                appInBuffer = ByteBuffer.allocateDirect(needed)
                src.position(offset)
                result = sslEngine.unwrap(src, appInBuffer)
                runDelegatedTasks()
            }
            if (result.status != SSLEngineResult.Status.OK) {
                OalLog.e(TAG, "decrypt unwrap failed: ${result.status}")
                return null
            }
            val out = ByteArray(result.bytesProduced())
            appInBuffer.flip()
            appInBuffer.get(out)
            out
        } catch (e: Exception) {
            OalLog.e(TAG, "decrypt error: ${e.message}")
            null
        }
    }

    fun release() {
        try {
            if (::sslEngine.isInitialized) {
                sslEngine.closeOutbound()
            }
        } catch (_: Exception) {}
    }

    private fun runDelegatedTasks() {
        while (sslEngine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            sslEngine.delegatedTask?.run() ?: break
        }
    }

    private fun createSslContextInternal(): SSLContext {
        // Use the standard AA headunit certificate — same one used by aasdk,
        // HURev, and every other AA headunit implementation. The phone doesn't
        // verify it (SSL_VERIFY_NONE) but expects a cert to be presented.
        OalLog.i(TAG, "Creating SSL context with standard AA headunit cert")

        val certPem = """
-----BEGIN CERTIFICATE-----
MIIDJTCCAg0CAnZTMA0GCSqGSIb3DQEBCwUAMFsxCzAJBgNVBAYTAlVTMRMwEQYD
VQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MR8wHQYDVQQK
DBZHb29nbGUgQXV0b21vdGl2ZSBMaW5rMB4XDTE0MDcwODIyNDkxOFoXDTQ0MDcw
NzIyNDkxOFowVTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1N
b3VudGFpbiBWaWV3MSEwHwYDVQQKDBhHb29nbGUtQW5kcm9pZC1SZWZlcmVuY2Uw
ggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCpqQmvoDW/XsREoj20dRcM
qJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN6BxoNHpP
giZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPtEz5AsKi7
c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9GdPZh/ezS
m2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCgtfzwKPqb
nJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i/9IKaC69
AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAIpfjQriEtbpUyWLoOOfJsjFN04+ajq9
1XALCPd+2ixWHZIBJiucrrf0H7OgY7eFnNbU0cRqiDZHI8BtvzFxNi/JgXqCmSHR
rlaoIsITfqo8KHwcAMs4qWTeLQmkTXBZYz0M3HwC7N1vOGjAJJN5qENIm1Jq+/3c
fxVg2zhHPKY8qtdgl73YIXb9Xx3WmPCBeRBCKJncj0Rq14uaOjWXRyBgbmdzMXJz
FGPHx3wN04JqGyfPFlDazXExFQwuAryjoYBRdxPxGufeQCp3am4xxI2oxNIzR+4L
nOcDhgU1B7sbkVzbKj5gjdOQAmxnKCfBtUNB63a7yzGPYGPIwlBsm54=
-----END CERTIFICATE-----""".trimIndent()

        val keyPem = """
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCpqQmvoDW/XsRE
oj20dRcMqJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN
6BxoNHpPgiZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPt
Ez5AsKi7c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9G
dPZh/ezSm2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCg
tfzwKPqbnJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i
/9IKaC69AgMBAAECggEAbBoW3963IG6jpA+0PW11+EzYJw/u5ZiCsS3z3s0Fd6E7
VqBIQyXU8FOlpxMSvQ8zqtaVjroGLlIsS88feo4leM+28Qm70I8W/I7jPDPcmxlS
nbqycnDu5EY5IeVi27eAUI+LUbBs3APb900Rl2p4uKfoBkAlC0yjI5J1GcczZhf7
RDh1wGgFWZI+ljiSrfpdiA4XmcZ9c7FlO5+NTotZzYeNx1iZprajV1/dlDy8UWEk
woWtppeGzUf3HHgl8yay62ub2vo5I1Z7Z98Roq8KC1o7k2IXOrHztCl3X03gMwlI
F4WQ6Fx5LZDU9dfaPhzkutekVgbtO9SzHgb3NXCZwQKBgQDcSS/OLll18ssjBwc7
PsdaIFIPlF428Tk8qezEnDmHS6xeztkGnpOlilk9jYSsVUbQmq8MwBSjfMVH95B0
w0yyfOYqjgTocg4lRCoPuBdnuBY/lU1Lws4FoGsGMNFkHWjHzl622mavkJiDzWA+
CORPUllS/DnPKJnZk2n0zZRKaQKBgQDFKqvePMx/a/ayQ09UZYxov0vwRyNkHevm
wEGQjOiHKozWvLqWhCvFtwo+VqHqmCw95cYUpg1GvppB6Lnw2uHgWAWxr3ugDjaR
YSqG/L7FG6FDF+1sPvBuxNpBmto59TI1fBFmU9VBGLDnr1M27qH3KTWlA3lCsovV
6Dbk7D+vNQKBgE6GgFYdS6KyFBu+a6OA84t7LgWDvDoVr3Oil1ZW4mMKZL2/OroT
WUqPkNRSWFMeawn9uhzvc+v7lE/dPk+BNxwBTgMpcTJzRfue2ueTljRQ+Q1daZpy
LQLwdnZUfLAVk752IGlKXYSEJPoHAiHbBZgJIPJmGy1vqbhXxlOP3SbRAoGBAJoA
Q2/5gy0/sdf5FRxxmOM0D+dkWTNY36pDnrJ+LR1uUcVkckUghWQQHRMl7aBkLaJH
N5lnPdV1CN3UHnAPNwBZIFFyJJiWoW6aO3JmNceVVjcmmE7FNlz+qw81GaDNcOMv
vhN0BYyr8Xl1iwTMDXwVFw6FkRBUjz6L+1yBXxjFAoGAJZcU+tEM1+gHPCqHK2bP
kfYOCyEAro4zY/VWXZKHgCoPau8Uc9+vFu2QVMb5kVyLTdyRLQKpooR6f8En6utS
/G15YuqRYqzSTrMBzpRrqIwbgKI9RHNPAvhtVAmXnwsYDPIQ1rrELK6WzTjUySRd
7gyCoq+DlY7ZKDa7FUz05Ek=
-----END PRIVATE KEY-----""".trimIndent()

        // Parse cert
        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(certPem.byteInputStream()) as X509Certificate

        // Parse private key
        val keyBase64 = keyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
        val keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.DEFAULT)
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
        val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        // Build KeyStore
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setKeyEntry("headunit", privateKey, charArrayOf(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, charArrayOf())

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val ctx = try {
            SSLContext.getInstance("TLS", "Conscrypt")
        } catch (_: Exception) {
            SSLContext.getInstance("TLS")
        }
        ctx.init(kmf.keyManagers, trustAll, null)
        return ctx
    }
}
