/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.dex

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DexClassModel(
    val descriptor: String,
    val simpleName: String,
    val packageName: String,
    val superclass: String,
    val interfaces: List<String>,
    val accessFlags: Int,
    val accessFlagsString: String,
    val isInterface: Boolean,
    val isEnum: Boolean,
    val isAbstract: Boolean,
    val methodCount: Int,
    val fieldCount: Int,
    val sourceFile: String?
) : Parcelable {
    val badgeLetter: String
        get() = when {
            isInterface -> "I"
            isEnum -> "E"
            isAbstract -> "A"
            else -> "C"
        }

    val badgeColor: Int
        get() = when {
            isInterface -> 0xFF4CAF50.toInt() // Green
            isEnum -> 0xFFAB47BC.toInt()      // Purple
            isAbstract -> 0xFFFFA726.toInt()  // Orange
            else -> 0xFF2196F3.toInt()        // Blue
        }
}

@Parcelize
data class DexMethodModel(
    val name: String,
    val returnType: String,
    val parameterTypes: List<String>,
    val accessFlagsString: String,
    val isConstructor: Boolean
) : Parcelable {
    val signature: String
        get() = "(${parameterTypes.joinToString(", ") { formatType(it) }}): ${formatType(returnType)}"
}

@Parcelize
data class DexFieldModel(
    val name: String,
    val type: String,
    val accessFlagsString: String,
    val initialValue: String?
) : Parcelable {
    val displayType: String
        get() = formatType(type)
}

data class DexStringModel(
    val index: Int,
    var value: String
)

fun formatType(descriptor: String): String {
    if (descriptor.isEmpty()) return ""
    return when (descriptor) {
        "V" -> "void"
        "Z" -> "boolean"
        "B" -> "byte"
        "S" -> "short"
        "C" -> "char"
        "I" -> "int"
        "J" -> "long"
        "F" -> "float"
        "D" -> "double"
        else -> {
            if (descriptor.startsWith("[")) {
                formatType(descriptor.substring(1)) + "[]"
            } else if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                descriptor.substring(1, descriptor.length - 1).replace('/', '.')
            } else {
                descriptor
            }
        }
    }
}
