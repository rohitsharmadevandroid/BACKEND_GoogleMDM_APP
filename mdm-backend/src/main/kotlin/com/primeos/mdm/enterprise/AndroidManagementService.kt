package com.primeos.mdm.enterprise

import com.google.api.services.androidmanagement.v1.AndroidManagement
import com.google.api.services.androidmanagement.v1.model.Command as GoogleCommand
import com.google.api.services.androidmanagement.v1.model.EnrollmentToken as GoogleEnrollmentToken
import com.google.api.services.androidmanagement.v1.model.Enterprise
import com.google.api.services.androidmanagement.v1.model.MigrationToken
import com.google.api.services.androidmanagement.v1.model.Operation
import com.google.api.services.androidmanagement.v1.model.Policy as GooglePolicy
import com.google.api.services.androidmanagement.v1.model.SignupUrl
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AndroidManagementService(
    private val androidManagement: AndroidManagement,
    @Value("\${mdm.gcp.project-id}")
    private val projectId: String,
) {

    // The first real call in the enterprise-creation flow: an admin visits
    // the returned url, completes Google's enterprise sign-up UI, and Google
    // redirects to callbackUrl with a completion token you then pass to
    // enterprises.create. A successful response here is the smoke test that
    // the service account is authenticated and holds the
    // androidmanagement.user role.
    fun createSignupUrl(callbackUrl: String): SignupUrl =
        androidManagement.signupUrls()
            .create()
            .setProjectId(projectId)
            .setCallbackUrl(callbackUrl)
            .execute()

    // Second and final call in the flow: turns a completed sign-up into an
    // actual enterprise resource. signupUrlName must be the exact one the
    // enterpriseToken was issued for - Google validates that pairing itself.
    fun createEnterprise(signupUrlName: String, enterpriseToken: String, displayName: String): Enterprise =
        androidManagement.enterprises()
            .create(Enterprise().setEnterpriseDisplayName(displayName))
            .setProjectId(projectId)
            .setSignupUrlName(signupUrlName)
            .setEnterpriseToken(enterpriseToken)
            .execute()

    // policyName must reference a policy that already exists under this
    // enterprise (e.g. "enterprises/{id}/policies/default"). We don't
    // resolve that from our internal `policies` table yet - that resolution
    // is step #5's translator; for now the caller supplies the real Google
    // policy resource name directly.
    fun createEnrollmentToken(
        enterpriseName: String,
        policyName: String,
        oneTimeOnly: Boolean,
        allowPersonalUsage: String,
        durationSeconds: Long?,
    ): GoogleEnrollmentToken {
        var body = GoogleEnrollmentToken()
            .setPolicyName(policyName)
            .setOneTimeOnly(oneTimeOnly)
            .setAllowPersonalUsage(allowPersonalUsage)
        if (durationSeconds != null) {
            body = body.setDuration("${durationSeconds}s")
        }

        return androidManagement.enterprises().enrollmentTokens()
            .create(enterpriseName, body)
            .execute()
    }

    // patch here is a full replace (no updateMask), since we always send
    // the complete translated Policy rather than a partial update.
    // policyName is the full resource name, e.g.
    // "enterprises/{id}/policies/{policyId}" - PATCH creates it if absent.
    fun upsertPolicy(policyName: String, policy: GooglePolicy): GooglePolicy =
        androidManagement.enterprises().policies()
            .patch(policyName, policy)
            .execute()

    // Returns an Operation, not a Command - issueCommand is async. The
    // Operation's name is what you'd poll via
    // enterprises.devices.operations.get (or learn about via Pub/Sub in
    // step #6) to find out if it actually completed.
    fun issueCommand(deviceName: String, command: GoogleCommand): Operation =
        androidManagement.enterprises().devices()
            .issueCommand(deviceName, command)
            .execute()

    // Unlike the WIPE command (issueCommand, which wipes but leaves the
    // device enrolled), this removes the device from management entirely -
    // the wipe here happens atomically as part of the same delete call, no
    // operation to poll. Returns Empty (a real, verified return type, not
    // Operation) - there's nothing meaningful to return to the caller.
    fun deleteDevice(deviceName: String, wipeDataFlags: List<String>, wipeReasonMessage: String?) {
        val request = androidManagement.enterprises().devices()
            .delete(deviceName)
            .setWipeDataFlags(wipeDataFlags)
        if (wipeReasonMessage != null) {
            request.setWipeReasonMessage(wipeReasonMessage)
        }
        request.execute()
    }

    // Mints a token for Google's documented DPC-migration flow (moving a
    // device that's currently managed by a third-party custom DPC over to
    // being managed via the Android Management API / Android Device
    // Policy), NOT a fresh enrollment - see enterprises.migrationTokens.create.
    // deviceId/userId are required, immutable, and NOT derivable from
    // anything else in this API - verified (javap on the real jar) that
    // they only exist as the output of a successful on-device
    // AccountSetupClient round trip (its resulting EnterpriseAccount
    // carries both), so the caller must supply real values obtained that
    // way, not anything this backend can compute itself. managementMode is
    // always "FULLY_MANAGED" here since every device eligible for this
    // migration is already Device Owner via the custom DPC - never a
    // work-profile scenario.
    fun createMigrationToken(
        enterpriseName: String,
        playDeviceId: String,
        playUserId: String,
        policyName: String,
        ttlSeconds: Long?,
        additionalData: String?,
    ): MigrationToken {
        var body = MigrationToken()
            .setDeviceId(playDeviceId)
            .setUserId(playUserId)
            .setPolicy(policyName)
            .setManagementMode("FULLY_MANAGED")
        if (ttlSeconds != null) {
            body = body.setTtl("${ttlSeconds}s")
        }
        if (additionalData != null) {
            body = body.setAdditionalData(additionalData)
        }

        return androidManagement.enterprises().migrationTokens()
            .create(enterpriseName, body)
            .execute()
    }
}
