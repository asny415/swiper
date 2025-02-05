package `fun`.wqiang.swiper

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.Date
import kotlin.random.Random

class AdbConnectionManager(context: Context) : AbsAdbConnectionManager() {
    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: Certificate? = null
    companion object {
        private var INSTANCE: AbsAdbConnectionManager? = null
        @JvmStatic
        fun getInstance(context: Context): AbsAdbConnectionManager {
            if (INSTANCE == null) {
                INSTANCE = AdbConnectionManager(context)
            }
            return INSTANCE as AbsAdbConnectionManager
        }
    }

    override fun getPrivateKey(): PrivateKey {
        return mPrivateKey!!
    }

    override fun getCertificate(): Certificate {
        return mCertificate!!
    }

    override fun getDeviceName(): String {
        return "Swiper"
    }

    init {
        api = Build.VERSION.SDK_INT
        mPrivateKey = readPrivateKeyFromFile(context)
        mCertificate = readCertificateFromFile(context)
        if (mPrivateKey == null) {
            val keySize = 2048
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
            val generateKeyPair = keyPairGenerator.generateKeyPair()
            val publicKey = generateKeyPair.public
            mPrivateKey = generateKeyPair.private

            val subject = "CN=Swiper"
            val algorithmName = "SHA512withRSA"
            val expireDate = System.currentTimeMillis() + 20L * 365 * 24 * 60 * 60 * 1000
            val certificateExtensions = CertificateExtensions()
            certificateExtensions.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier))
            val x500Name = X500Name(subject)
            val notBefore = Date()
            val notAfter = Date(expireDate)
            certificateExtensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore , notAfter))
            val certificateValidity = CertificateValidity(notBefore , notAfter)
            val x509CertInfo = X509CertInfo()
            x509CertInfo.set("version", CertificateVersion(2))
            x509CertInfo.set("serialNumber", CertificateSerialNumber(Random.Default.nextInt() and Int.MAX_VALUE))
            x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
            x509CertInfo.set("subject", CertificateSubjectName(x500Name))
            x509CertInfo.set("key", CertificateX509Key(publicKey))
            x509CertInfo.set("validity", certificateValidity)
            x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
            x509CertInfo.set("extensions", certificateExtensions)
            val x509CertImpl = X509CertImpl(x509CertInfo)
            x509CertImpl.sign(mPrivateKey,algorithmName)
            mCertificate = x509CertImpl
            writePrivateKeyToFile(context, mPrivateKey!!)
            writeCertificateToFile(context, mCertificate!!)
        }
    }

    private fun writeCertificateToFile(context: Context, mCertificate: Certificate) {
        val certFile = File(context.filesDir, "cert.pem")
        val encoder  = BASE64Encoder()
        val os = FileOutputStream(certFile)
        os.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
        os.write("\n".toByteArray())
        encoder.encode(mCertificate.encoded, os)
        os.write("\n".toByteArray())
        os.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writePrivateKeyToFile(context: Context, mPrivateKey: PrivateKey) {
        val privateKeyFile = File(context.filesDir, "private.key")
        val os = FileOutputStream(privateKeyFile)
        os.write(mPrivateKey.encoded)
    }

    private fun readCertificateFromFile(context:Context) : Certificate? {
        val certFile = File(context.filesDir, "cert.pem")
        if (!certFile.exists()) return null
        try {
            return CertificateFactory.getInstance("X.509").generateCertificate(certFile.inputStream())
        } catch (th:Throwable) {
            th.printStackTrace()
        }
        return null
    }

    private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
        val privateKeyFile = File(context.filesDir, "private.key")
        if (!privateKeyFile.exists()) return null
        try {
            val privateKeyBytes = ByteArray(privateKeyFile.length().toInt())
            val iss = privateKeyFile.inputStream()
            iss.read(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
            return keyFactory.generatePrivate(privateKeySpec)
        } catch (th: Throwable) {
            th.printStackTrace()
        }
        return null
    }
}