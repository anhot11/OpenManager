/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java8.nio.file.FileSystems
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

object ApkSignerHelper {

    enum class SignScheme {
        V1_AND_V2,
        V1_ONLY,
        V2_ONLY
    }

    private var cachedKeyPair: Pair<PrivateKey, X509Certificate>? = null

    @Synchronized
    fun getTestKeyPair(): Pair<PrivateKey, X509Certificate> {
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

    fun loadCustomKeystore(
        file: File,
        storePass: String,
        alias: String,
        keyPass: String
    ): Pair<PrivateKey, X509Certificate> {
        val ksType = if (file.name.endsWith(".p12", true) || file.name.endsWith(".pfx", true)) "PKCS12" else "JKS"
        val ks = KeyStore.getInstance(ksType)
        FileInputStream(file).use { ks.load(it, storePass.toCharArray()) }
        val privateKey = ks.getKey(alias, keyPass.toCharArray()) as? PrivateKey
            ?: throw IllegalArgumentException("No se encontró la clave privada para el alias: $alias")
        val cert = ks.getCertificate(alias) as? X509Certificate
            ?: throw IllegalArgumentException("No se encontró el certificado para el alias: $alias")
        return Pair(privateKey, cert)
    }

    fun signApk(
        activity: Activity,
        apkPath: Path,
        onComplete: ((Path?) -> Unit)? = null
    ) {
        showSignDialog(activity, apkPath, onComplete)
    }

    /**
     * Shows MT Manager style dialog for signing APK with:
     * 1. 3 signing schemes: V1+V2 (recommended), V1 (JAR), V2 (Android 7.0+)
     * 2. Keystore options: testkey (AOSP) or custom keystore (.jks/.keystore)
     * 3. Output file name and overwrite option
     */
    fun showSignDialog(
        activity: Activity,
        apkPath: Path,
        onComplete: ((Path?) -> Unit)? = null
    ) {
        val context = activity
        val dialog = AppCompatDialog(context)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_mt_sign_apk, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = (displayMetrics.widthPixels * 0.92).toInt().coerceAtMost(
                (450 * displayMetrics.density).toInt()
            )
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val fileName = apkPath.fileName.toString()
        val baseName = if (fileName.endsWith(".apk", ignoreCase = true)) {
            fileName.substringBeforeLast(".apk")
        } else {
            fileName
        }
        val defaultTargetFileName = "${baseName}_signed.apk"

        val editOutputName = view.findViewById<EditText>(R.id.editOutputName)
        editOutputName.setText(defaultTargetFileName)

        val rbSchemeV1V2 = view.findViewById<RadioButton>(R.id.rbSchemeV1V2)
        val rbSchemeV1 = view.findViewById<RadioButton>(R.id.rbSchemeV1)
        val rbSchemeV2 = view.findViewById<RadioButton>(R.id.rbSchemeV2)

        val rbKeyTestkey = view.findViewById<RadioButton>(R.id.rbKeyTestkey)
        val rbKeyCustom = view.findViewById<RadioButton>(R.id.rbKeyCustom)
        val layoutCustomKeyOptions = view.findViewById<LinearLayout>(R.id.layoutCustomKeyOptions)

        val editKeystorePath = view.findViewById<EditText>(R.id.editKeystorePath)
        val btnBrowseKeystore = view.findViewById<Button>(R.id.btnBrowseKeystore)
        val editKeystorePass = view.findViewById<EditText>(R.id.editKeystorePass)
        val editKeyAlias = view.findViewById<EditText>(R.id.editKeyAlias)
        val editKeyPass = view.findViewById<EditText>(R.id.editKeyPass)

        val cbOverwrite = view.findViewById<CheckBox>(R.id.cbOverwrite)
        val btnCancelSign = view.findViewById<Button>(R.id.btnCancelSign)
        val btnConfirmSign = view.findViewById<Button>(R.id.btnConfirmSign)

        val rgKeystore = view.findViewById<RadioGroup>(R.id.rgKeystore)
        rgKeystore.setOnCheckedChangeListener { _, checkedId ->
            layoutCustomKeyOptions.visibility = if (checkedId == R.id.rbKeyCustom) View.VISIBLE else View.GONE
        }

        cbOverwrite.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                editOutputName.setText(fileName)
                editOutputName.isEnabled = false
            } else {
                editOutputName.setText(defaultTargetFileName)
                editOutputName.isEnabled = true
            }
        }

        btnBrowseKeystore.setOnClickListener {
            // Prompt to enter path or default common location
            val defaultPath = "/storage/emulated/0/Download"
            if (editKeystorePath.text.isNullOrEmpty()) {
                editKeystorePath.setText("$defaultPath/my-release-key.jks")
            }
            Toast.makeText(context, "Ingresa la ruta absoluta de tu archivo .jks o .keystore", Toast.LENGTH_SHORT).show()
        }

        btnCancelSign.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmSign.setOnClickListener {
            val outputName = editOutputName.text.toString().trim()
            if (outputName.isEmpty()) {
                Toast.makeText(context, "Por favor ingresa un nombre válido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val scheme = when {
                rbSchemeV1.isChecked -> SignScheme.V1_ONLY
                rbSchemeV2.isChecked -> SignScheme.V2_ONLY
                else -> SignScheme.V1_AND_V2
            }

            val useCustomKey = rbKeyCustom.isChecked
            val customKeyPath = editKeystorePath.text.toString().trim()
            val customKeyStorePass = editKeystorePass.text.toString()
            val customKeyAlias = editKeyAlias.text.toString().trim()
            val customKeyPass = editKeyPass.text.toString()

            if (useCustomKey && (customKeyPath.isEmpty() || customKeyAlias.isEmpty())) {
                Toast.makeText(context, "Por favor completa la ruta y alias del KeyStore", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            signApkInternal(
                activity = activity,
                apkPath = apkPath,
                outputFileName = outputName,
                scheme = scheme,
                useCustomKey = useCustomKey,
                customKeyPath = customKeyPath,
                customKeyStorePass = customKeyStorePass,
                customKeyAlias = customKeyAlias,
                customKeyPass = customKeyPass,
                onComplete = onComplete
            )
        }

        dialog.show()
    }

    private fun signApkInternal(
        activity: Activity,
        apkPath: Path,
        outputFileName: String,
        scheme: SignScheme,
        useCustomKey: Boolean,
        customKeyPath: String,
        customKeyStorePass: String,
        customKeyAlias: String,
        customKeyPass: String,
        onComplete: ((Path?) -> Unit)?
    ) {
        val parentDir = apkPath.parent ?: Paths.get("/storage/emulated/0/Download")
        val targetPath = parentDir.resolve(outputFileName)

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Firmando APK")
            .setMessage("Aplicando esquema de firma ${scheme.name}...\nPor favor espera.")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var errorMsg: String? = null
            var tempInFile: File? = null
            var tempOutFile: File? = null

            try {
                // 1. Resolve key pair
                val (privateKey, cert) = if (useCustomKey) {
                    val kf = File(customKeyPath)
                    if (!kf.exists() || !kf.canRead()) {
                        throw IllegalArgumentException("No se puede leer el archivo KeyStore:\n$customKeyPath")
                    }
                    loadCustomKeystore(kf, customKeyStorePass, customKeyAlias, customKeyPass)
                } else {
                    getTestKeyPair()
                }

                // 2. Prepare local files for ApkSigner
                tempInFile = if (apkPath.fileSystem == FileSystems.getDefault()) {
                    File(apkPath.toString())
                } else {
                    val temp = File.createTempFile("apk_in_", ".apk", activity.cacheDir)
                    apkPath.newInputStream().use { ins ->
                        temp.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    temp
                }

                tempOutFile = File.createTempFile("apk_out_", ".apk", activity.cacheDir)

                // 3. Configure Google ApkSigner
                val signerConfig = ApkSigner.SignerConfig.Builder(
                    "CERT",
                    privateKey,
                    listOf(cert)
                ).build()

                val v1Enabled = (scheme == SignScheme.V1_AND_V2 || scheme == SignScheme.V1_ONLY)
                val v2Enabled = (scheme == SignScheme.V1_AND_V2 || scheme == SignScheme.V2_ONLY)
                val v3Enabled = (scheme == SignScheme.V1_AND_V2)

                val builder = ApkSigner.Builder(listOf(signerConfig))
                    .setInputApk(tempInFile)
                    .setOutputApk(tempOutFile)
                    .setV1SigningEnabled(v1Enabled)
                    .setV2SigningEnabled(v2Enabled)
                    .setV3SigningEnabled(v3Enabled)

                val signer = builder.build()
                signer.sign()

                // 4. Copy output file to destination
                if (targetPath.fileSystem == FileSystems.getDefault()) {
                    val destFile = File(targetPath.toString())
                    tempOutFile.copyTo(destFile, overwrite = true)
                } else {
                    tempOutFile.inputStream().use { ins ->
                        targetPath.newOutputStream().use { outs -> ins.copyTo(outs) }
                    }
                }

                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            } finally {
                if (tempInFile != null && tempInFile != File(apkPath.toString())) {
                    tempInFile.delete()
                }
                tempOutFile?.delete()
            }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                try { progressDialog.dismiss() } catch (ignored: Exception) {}
                if (success) {
                    Toast.makeText(
                        activity,
                        "APK firmado exitosamente (${scheme.name}):\n$outputFileName",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete?.invoke(targetPath)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al firmar APK")
                        .setMessage(errorMsg ?: "Error desconocido al procesar el archivo")
                        .setPositiveButton("Aceptar", null)
                        .show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    /**
     * Inspects and verifies the APK signatures (V1, V2, V3) and shows certificate information.
     */
    fun showVerifySignatureDialog(activity: Activity, apkPath: Path) {
        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Verificando firma")
            .setMessage("Comprobando firmas V1, V2 y certificados...")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var tempFile: File? = null
            var v1Ok = false
            var v2Ok = false
            var v3Ok = false
            var certSummary = "No se encontraron certificados"
            var md5Hex = ""
            var sha1Hex = ""
            var sha256Hex = ""
            var errorMsg: String? = null

            try {
                tempFile = if (apkPath.fileSystem == FileSystems.getDefault()) {
                    File(apkPath.toString())
                } else {
                    val temp = File.createTempFile("verify_", ".apk", activity.cacheDir)
                    apkPath.newInputStream().use { ins ->
                        temp.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    temp
                }

                val verifier = ApkVerifier.Builder(tempFile).build()
                val result = verifier.verify()
                v1Ok = result.isVerifiedUsingV1Scheme
                v2Ok = result.isVerifiedUsingV2Scheme
                v3Ok = result.isVerifiedUsingV3Scheme

                val certs = result.signerCertificates
                if (certs.isNotEmpty()) {
                    val c = certs[0]
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val sb = StringBuilder()
                    sb.append("Sujeto: ").append(c.subjectX500Principal.name).append("\n")
                    sb.append("Emisor: ").append(c.issuerX500Principal.name).append("\n")
                    sb.append("Válido desde: ").append(df.format(c.notBefore)).append("\n")
                    sb.append("Válido hasta: ").append(df.format(c.notAfter)).append("\n")
                    sb.append("Número de serie: ").append(c.serialNumber.toString(16)).append("\n")
                    certSummary = sb.toString()

                    val der = c.encoded
                    md5Hex = MessageDigest.getInstance("MD5").digest(der).joinToString(":") { "%02X".format(it) }
                    sha1Hex = MessageDigest.getInstance("SHA-1").digest(der).joinToString(":") { "%02X".format(it) }
                    sha256Hex = MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            } finally {
                if (tempFile != null && tempFile != File(apkPath.toString())) {
                    tempFile.delete()
                }
            }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                try { progressDialog.dismiss() } catch (ignored: Exception) {}
                if (errorMsg != null) {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al verificar firma")
                        .setMessage(errorMsg)
                        .setPositiveButton("Cerrar", null)
                        .show()
                    return@runOnUiThread
                }

                val msg = StringBuilder()
                msg.append("Archivo: ").append(apkPath.fileName).append("\n\n")
                msg.append("• Esquema V1 (JAR): ").append(if (v1Ok) "✓ Válido" else "✗ No presente / Inválido").append("\n")
                msg.append("• Esquema V2 (Android 7.0+): ").append(if (v2Ok) "✓ Válido" else "✗ No presente / Inválido").append("\n")
                msg.append("• Esquema V3 (Android 9.0+): ").append(if (v3Ok) "✓ Válido" else "✗ No presente / Inválido").append("\n\n")
                msg.append("Información del Certificado:\n")
                msg.append(certSummary).append("\n")
                if (sha256Hex.isNotEmpty()) {
                    msg.append("MD5:\n").append(md5Hex).append("\n\n")
                    msg.append("SHA-1:\n").append(sha1Hex).append("\n\n")
                    msg.append("SHA-256:\n").append(sha256Hex)
                }

                val fullText = msg.toString()
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Verificación de Firma")
                    .setMessage(fullText)
                    .setPositiveButton("Aceptar", null)
                    .setNeutralButton("Copiar") { _, _ ->
                        val cb = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Firma APK", fullText))
                        Toast.makeText(activity, "Información copiada al portapapeles", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
}
