package com.primeos.mdm.device

import java.util.UUID

data class GmsMigrationTokenRequest(
    // From the on-device AccountSetupClient's resulting EnterpriseAccount
    // once account setup reaches the addedAccount state - NOT the same
    // thing as this endpoint's own {deviceId} path variable (our internal
    // Device id). See DeviceMigrationService for the real chain this comes
    // from; this backend cannot derive it itself.
    val playDeviceId: String,
    // From that same EnterpriseAccount.
    val playUserId: String,
    // The policy to apply once migration completes - synced to Google
    // as part of this call, same as every other GMS policy push.
    val policyId: UUID,
    val ttlSeconds: Long? = null,
    val additionalData: String? = null,
)
