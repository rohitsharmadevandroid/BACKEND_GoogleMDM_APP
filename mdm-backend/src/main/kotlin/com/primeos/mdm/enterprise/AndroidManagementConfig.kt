package com.primeos.mdm.enterprise

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidmanagement.v1.AndroidManagement
import com.google.api.services.androidmanagement.v1.AndroidManagementScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class AndroidManagementConfig(
    @Value("\${mdm.gcp.android-management-credentials-path}")
    private val credentialsLocation: Resource,
) {

    @Bean
    fun androidManagement(): AndroidManagement {
        val credentials = credentialsLocation.inputStream
            .use { GoogleCredentials.fromStream(it) }
            .createScoped(AndroidManagementScopes.ANDROIDMANAGEMENT)

        return AndroidManagement.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials),
        )
            .setApplicationName("mdm-backend")
            .build()
    }
}
