/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java8.nio.file.Path
import java8.nio.file.Paths
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.provider.common.deleteIfExists
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream

object FileEncryptDecryptHelper {

    private val MAGIC_HEADER = "OMENC1".toByteArray(Charsets.UTF_8)
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256

    fun isEncryptedFile(file: FileItem): Boolean {
        return file.name.endsWith(".enc", ignoreCase = true)
    }

    fun showEncryptDialog(activity: Activity, file: FileItem, onDone: () -> Unit) {
        val context = activity
        val pad = (16 * context.resources.displayMetrics.density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val etPassword = EditText(context).apply {
            hint = "Contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etConfirm = EditText(context).apply {
            hint = "Confirmar contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val cbDeleteOriginal = CheckBox(context).apply {
            text = "Eliminar archivo original después de cifrar"
            isChecked = false
        }

        layout.addView(etPassword)
        layout.addView(etConfirm)
        layout.addView(cbDeleteOriginal)

        MaterialAlertDialogBuilder(context)
            .setTitle("Cifrar archivo")
            .setMessage("Cifrar '${file.name}' con AES-256")
            .setView(layout)
            .setPositiveButton("Cifrar") { _, _ ->
                val pwd = etPassword.text.toString()
                val confirm = etConfirm.text.toString()
                if (pwd.isEmpty()) {
                    Toast.makeText(context, "La contraseña no puede estar vacía", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pwd != confirm) {
                    Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                encrypt(activity, file.path, pwd, cbDeleteOriginal.isChecked, onDone)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun showDecryptDialog(activity: Activity, file: FileItem, onDone: () -> Unit) {
        val context = activity
        val pad = (16 * context.resources.displayMetrics.density).toInt()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val etPassword = EditText(context).apply {
            hint = "Contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val cbDeleteEnc = CheckBox(context).apply {
            text = "Eliminar archivo cifrado después de descifrar"
            isChecked = false
        }

        layout.addView(etPassword)
        layout.addView(cbDeleteEnc)

        MaterialAlertDialogBuilder(context)
            .setTitle("Descifrar archivo")
            .setMessage("Descifrar '${file.name}'")
            .setView(layout)
            .setPositiveButton("Descifrar") { _, _ ->
                val pwd = etPassword.text.toString()
                if (pwd.isEmpty()) {
                    Toast.makeText(context, "Introduce la contraseña", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                decrypt(activity, file.path, pwd, cbDeleteEnc.isChecked, onDone)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun encrypt(
        activity: Activity,
        sourcePath: Path,
        password: String,
        deleteOriginal: Boolean,
        onDone: () -> Unit
    ) {
        val fileName = sourcePath.fileName.toString()
        val targetName = "$fileName.enc"
        val targetPath = (sourcePath.parent ?: Paths.get("/storage/emulated/0/Download")).resolve(targetName)

        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle("Cifrando archivo")
            .setMessage("Cifrando $fileName con AES-256...")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var errorMsg: String? = null
            try {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

                val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val keyBytes = factory.generateSecret(keySpec).encoded
                val secretKey = SecretKeySpec(keyBytes, "AES")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

                sourcePath.newInputStream().use { inStream ->
                    targetPath.newOutputStream().use { outStream ->
                        outStream.write(MAGIC_HEADER)
                        outStream.write(salt)
                        outStream.write(iv)

                        val cipherOut = CipherOutputStream(outStream, cipher)
                        val buf = ByteArray(65536)
                        var read: Int
                        while (inStream.read(buf).also { read = it } != -1) {
                            cipherOut.write(buf, 0, read)
                        }
                        cipherOut.flush()
                        cipherOut.close()
                    }
                }

                if (deleteOriginal) {
                    try {
                        sourcePath.deleteIfExists()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                try {
                    targetPath.deleteIfExists()
                } catch (ignored: Exception) {}
            }

            activity.runOnUiThread {
                progress.dismiss()
                if (success) {
                    Toast.makeText(activity, "Archivo cifrado:\n$targetName", Toast.LENGTH_LONG).show()
                    onDone()
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al cifrar")
                        .setMessage(errorMsg ?: "Error desconocido")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
        }
    }

    private fun decrypt(
        activity: Activity,
        sourcePath: Path,
        password: String,
        deleteEnc: Boolean,
        onDone: () -> Unit
    ) {
        val fileName = sourcePath.fileName.toString()
        val targetName = if (fileName.endsWith(".enc", ignoreCase = true)) {
            fileName.substringBeforeLast(".enc")
        } else {
            "${fileName}_decrypted"
        }
        val targetPath = (sourcePath.parent ?: Paths.get("/storage/emulated/0/Download")).resolve(targetName)

        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle("Descifrando archivo")
            .setMessage("Descifrando $fileName...")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var isWrongPassword = false
            var errorMsg: String? = null
            try {
                sourcePath.newInputStream().use { inStream ->
                    val magic = ByteArray(MAGIC_HEADER.size)
                    inStream.read(magic)
                    if (!magic.contentEquals(MAGIC_HEADER)) {
                        throw IllegalArgumentException("Formato no compatible o archivo no cifrado por OpenManager")
                    }

                    val salt = ByteArray(16)
                    inStream.read(salt)
                    val iv = ByteArray(16)
                    inStream.read(iv)

                    val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val keyBytes = factory.generateSecret(keySpec).encoded
                    val secretKey = SecretKeySpec(keyBytes, "AES")

                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

                    targetPath.newOutputStream().use { outStream ->
                        val cipherIn = CipherInputStream(inStream, cipher)
                        val buf = ByteArray(65536)
                        var read: Int
                        try {
                            while (cipherIn.read(buf).also { read = it } != -1) {
                                outStream.write(buf, 0, read)
                            }
                            cipherIn.close()
                        } catch (e: Exception) {
                            isWrongPassword = true
                            throw e
                        }
                    }
                }

                if (deleteEnc) {
                    try {
                        sourcePath.deleteIfExists()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = if (isWrongPassword || e is javax.crypto.BadPaddingException) {
                    "Contraseña incorrecta"
                } else {
                    e.localizedMessage ?: e.javaClass.simpleName
                }
                try {
                    targetPath.deleteIfExists()
                } catch (ignored: Exception) {}
            }

            activity.runOnUiThread {
                progress.dismiss()
                if (success) {
                    Toast.makeText(activity, "Archivo descifrado:\n$targetName", Toast.LENGTH_LONG).show()
                    onDone()
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al descifrar")
                        .setMessage(errorMsg ?: "Error desconocido")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
        }
    }
}
