package com.primeos.mdm.device

data class GmsMigrationTokenResponse(
    // The actual value the app's DpcMigrationClient.migrate() call needs -
    // treat this as a bearer credential, same care as any enrollment token.
    val value: String?,
    // Resource name: enterprises/{enterprise}/migrationTokens/{migrationToken}
    val name: String?,
    val expireTime: String?,
)
