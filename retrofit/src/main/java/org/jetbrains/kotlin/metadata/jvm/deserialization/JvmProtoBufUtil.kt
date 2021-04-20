/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.metadata.jvm.deserialization

import com.google.protobuf.ExtensionRegistryLite
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.*
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf
import java.io.ByteArrayInputStream
import java.io.InputStream


object JvmProtoBufUtil {
    val EXTENSION_REGISTRY: ExtensionRegistryLite = ExtensionRegistryLite.newInstance().apply(JvmProtoBuf::registerAllExtensions)

    @JvmStatic
    fun readClassDataFrom(data: Array<String>, strings: Array<String>): Pair<JvmNameResolver, ProtoBuf.Class> =
        readClassDataFrom(BitEncoding.decodeBytes(data), strings)

    @JvmStatic
    fun readClassDataFrom(bytes: ByteArray, strings: Array<String>): Pair<JvmNameResolver, ProtoBuf.Class> {
        val input = ByteArrayInputStream(bytes)
        return Pair(input.readNameResolver(strings), ProtoBuf.Class.parseFrom(input, EXTENSION_REGISTRY))
    }

    private fun InputStream.readNameResolver(strings: Array<String>): JvmNameResolver =
        JvmNameResolver(JvmProtoBuf.StringTableTypes.parseDelimitedFrom(this, EXTENSION_REGISTRY), strings)

    // returns JVM signature in the format: "equals(Ljava/lang/Object;)Z"
    fun getJvmMethodSignature(
        proto: ProtoBuf.Function,
        nameResolver: NameResolver,
        typeTable: TypeTable
    ): JvmMemberSignature.Method? {
        val signature = proto.getExtensionOrNull(JvmProtoBuf.methodSignature)
        val name = if (signature != null && signature.hasName()) signature.name else proto.name
        val desc = if (signature != null && signature.hasDesc()) {
            nameResolver.getString(signature.desc)
        } else {
            val parameterTypes = listOfNotNull(proto.receiverType(typeTable)) + proto.valueParameterList.map { it.type(typeTable) }

            val parametersDesc = parameterTypes.map { mapTypeDefault(it, nameResolver) ?: return null }
            val returnTypeDesc = mapTypeDefault(proto.returnType(typeTable), nameResolver) ?: return null

            parametersDesc.joinToString(separator = "", prefix = "(", postfix = ")") + returnTypeDesc
        }
        return JvmMemberSignature.Method(nameResolver.getString(name), desc)
    }

    private fun mapTypeDefault(type: ProtoBuf.Type, nameResolver: NameResolver): String? {
        return if (type.hasClassName()) ClassMapperLite.mapClass(nameResolver.getQualifiedClassName(type.className)) else null
    }
}
