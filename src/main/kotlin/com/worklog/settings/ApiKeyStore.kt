package com.worklog.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
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
        return CredentialAttributes("$SERVICE_NAME:$configId")
    }
}
