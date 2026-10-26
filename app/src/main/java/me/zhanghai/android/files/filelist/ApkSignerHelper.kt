/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder

object ApkSignerHelper {

    private var cachedKeyPair: Pair<PrivateKey, X509Certificate>? = null

    @Synchronized
    private fun getTestKeyPair(): Pair<PrivateKey, X509Certificate> {
        cachedKeyPair?.let { return it }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val startDate = Date(now - 86400000L)
        val endDate = Date(now + 30L * 365 * 86400000L)
        val serial = BigInteger.valueOf(now)
        val owner = X500Name("CN=Android,O=Android,C=US")
        val certBuilder = JcaX509v3CertificateBuilder(
            owner, serial, startDate, endDate, owner, kp.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(certHolder)
        val pair = Pair(kp.private, cert)
        cachedKeyPair = pair
        return pair
    }

    fun signApk(
        activity: Activity,
        apkPath: Path,
        onComplete: ((Path?) -> Unit)? = null
    ) {
        val fileName = apkPath.fileName.toString()
        val baseName = if (fileName.endsWith(".apk", ignoreCase = true)) {
            fileName.substringBeforeLast(".apk")
        } else {
            fileName
        }
        val targetFileName = "${baseName}_signed.apk"
        val targetPath = (apkPath.parent ?: Paths.get("/storage/emulated/0/Download")).resolve(targetFileName)

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Firmando APK")
            .setMessage("Firmando $fileName...\nPor favor espera.")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var errorMsg: String? = null
            try {
                val (privateKey, cert) = getTestKeyPair()

                val manifest = Manifest()
                manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                manifest.mainAttributes[Attributes.Name("Created-By")] = "OpenManager MT Signer"

                val inStream = apkPath.newInputStream()
                val zipIn = ZipInputStream(inStream)

                val outStream = targetPath.newOutputStream()
                val zipOut = ZipOutputStream(outStream)

                val buffer = ByteArray(65536)
                var entry: ZipEntry? = zipIn.nextEntry

                while (entry != null) {
                    val name = entry.name
                    // Filter out existing signature files
                    val isSigFile = name.startsWith("META-INF/") && (
                        name.endsWith(".SF", ignoreCase = true) ||
                        name.endsWith(".RSA", ignoreCase = true) ||
                        name.endsWith(".DSA", ignoreCase = true) ||
                        name.endsWith(".EC", ignoreCase = true) ||
                        name.endsWith(".MF", ignoreCase = true)
                    )

                    if (!isSigFile && !entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            baos.write(buffer, 0, bytesRead)
                        }
                        val entryData = baos.toByteArray()

                        // Calculate SHA-1 digest for manifest
                        val sha1 = MessageDigest.getInstance("SHA-1").digest(entryData)
                        val sha1Base64 = Base64.encodeToString(sha1, Base64.NO_WRAP)

                        val attr = Attributes()
                        attr[Attributes.Name("SHA1-Digest")] = sha1Base64
                        manifest.entries[name] = attr

                        // Write entry to output
                        val newEntry = ZipEntry(name)
                        newEntry.time = entry.time
                        zipOut.putNextEntry(newEntry)
                        zipOut.write(entryData)
                        zipOut.closeEntry()
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                // 1. Write META-INF/MANIFEST.MF
                val mfBaos = ByteArrayOutputStream()
                manifest.write(mfBaos)
                val mfBytes = mfBaos.toByteArray()

                val mfEntry = ZipEntry("META-INF/MANIFEST.MF")
                zipOut.putNextEntry(mfEntry)
                zipOut.write(mfBytes)
                zipOut.closeEntry()

                // 2. Generate META-INF/CERT.SF
                val sfBuilder = StringBuilder()
                sfBuilder.append("Signature-Version: 1.0\r\n")
                sfBuilder.append("Created-By: OpenManager MT Signer\r\n")
                val mfSha1 = MessageDigest.getInstance("SHA-1").digest(mfBytes)
                sfBuilder.append("SHA1-Digest-Manifest: ${Base64.encodeToString(mfSha1, Base64.NO_WRAP)}\r\n\r\n")

                for ((entryName, attr) in manifest.entries) {
                    val entryMfChunk = "Name: $entryName\r\nSHA1-Digest: ${attr.getValue("SHA1-Digest")}\r\n\r\n"
                        .toByteArray(Charsets.UTF_8)
                    val chunkSha1 = MessageDigest.getInstance("SHA-1").digest(entryMfChunk)
                    val chunkSha1Base64 = Base64.encodeToString(chunkSha1, Base64.NO_WRAP)
                    sfBuilder.append("Name: $entryName\r\n")
                    sfBuilder.append("SHA1-Digest: $chunkSha1Base64\r\n\r\n")
                }
                val sfBytes = sfBuilder.toString().toByteArray(Charsets.UTF_8)

                val sfEntry = ZipEntry("META-INF/CERT.SF")
                zipOut.putNextEntry(sfEntry)
                zipOut.write(sfBytes)
                zipOut.closeEntry()

                // 3. Generate META-INF/CERT.RSA using BouncyCastle CMS
                val gen = CMSSignedDataGenerator()
                val contentSigner = JcaContentSignerBuilder("SHA1withRSA").build(privateKey)
                val digestProvider = JcaDigestCalculatorProviderBuilder().build()
                val signerInfoGen = JcaSignerInfoGeneratorBuilder(digestProvider).build(contentSigner, cert)
                gen.addSignerInfoGenerator(signerInfoGen)
                gen.addCertificates(JcaCertStore(listOf(cert)))

                val signedData = gen.generate(CMSProcessableByteArray(sfBytes), true)
                val rsaBytes = signedData.encoded

                val rsaEntry = ZipEntry("META-INF/CERT.RSA")
                zipOut.putNextEntry(rsaEntry)
                zipOut.write(rsaBytes)
                zipOut.closeEntry()

                zipOut.finish()
                zipOut.close()

                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            }

            activity.runOnUiThread {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(
                        activity,
                        "APK firmado exitosamente:\n$targetFileName",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete?.invoke(targetPath)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al firmar APK")
                        .setMessage(errorMsg ?: "Error desconocido")
                        .setPositiveButton("Aceptar", null)
                        .show()
                    onComplete?.invoke(null)
                }
            }
        }
    }
}
