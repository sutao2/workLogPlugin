package com.worklog.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object ApiKeyStore {
    private const val SERVICE_NAME = "WorkLog AI API"

    fun getApiKey(configId: String, fallback: String = ""): String {
        if (configId.isBlank()) return fallback
        return PasswordSafe.instance.getPassword(attributes(configId)) ?: fallback
    }

    fun setApiKey(configId: String, apiKey: String) {
        if (configId.isBlank()) return
        val credentials = apiKey.takeIf { it.isNotBlank() }?.let { Credentials(configId, it) }
        PasswordSafe.instance.set(attributes(configId), credentials)
    }

    private fun attributes(configId: String): CredentialAttributes {
        val serviceName = generateServiceName(SERVICE_NAME, configId)
        val attributesClass = CredentialAttributes::class.java
        val newConstructor = runCatching {
            attributesClass.getConstructor(
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
        }.getOrNull()

        val attributes = if (newConstructor != null) {
            newConstructor.newInstance(serviceName, configId, false, false)
        } else {
            attributesClass
                .getConstructor(
                    String::class.java,
                    String::class.java,
                    Class::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
                .newInstance(serviceName, configId, ApiKeyStore::class.java, false, false)
        }
        return attributes as CredentialAttributes
    }
}
