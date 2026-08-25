package com.primeos.mdm.dpc

import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceCredentialService
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceStatus
import com.primeos.mdm.device.DeviceType
import com.primeos.mdm.enrollment.EnrollmentTokenRepository
import com.primeos.mdm.enrollment.EnrollmentTokenStatus
import com.primeos.mdm.enrollment.InvalidEnrollmentTokenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DpcEnrollmentService(
    private val enrollmentTokenRepository: EnrollmentTokenRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceCredentialService: DeviceCredentialService,
    @Value("\${mdm.dpc.check-in-interval-seconds:60}")
    private val checkInIntervalSeconds: Long,
) {

    @Transactional
    fun enroll(request: DpcEnrollRequest): DpcEnrollResponse {
        val token = enrollmentTokenRepository.findByTokenValue(request.enrollmentToken)
            ?: throw InvalidEnrollmentTokenException("Unknown enrollment token")

        if (token.deviceType != DeviceType.NON_GMS) {
            throw InvalidEnrollmentTokenException("Not a non-GMS enrollment token")
        }
        if (token.status != EnrollmentTokenStatus.ACTIVE) {
            throw InvalidEnrollmentTokenException("Token is ${token.status}, not usable")
        }
        val expiresAt = token.expiresAt
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw InvalidEnrollmentTokenException("Token has expired")
        }
        if (token.usedCount >= token.maxUses) {
            throw InvalidEnrollmentTokenException("Token has reached its max uses")
        }

        val credential = deviceCredentialService.generate()

        val device = deviceRepository.save(
            Device(
                organization = token.organization,
                deviceType = DeviceType.NON_GMS,
                deviceUid = UUID.randomUUID().toString(),
                status = DeviceStatus.PROVISIONING,
                policy = token.defaultPolicy,
                enrollmentToken = token,
                credentialHash = credential.hash,
                credentialIssuedAt = Instant.now(),
            )
        )

        token.usedCount += 1
        if (token.usedCount >= token.maxUses) {
            token.status = EnrollmentTokenStatus.CONSUMED
        }
        // claimedByDevice only makes sense as "the one device" for
        // single-use tokens; for maxUses > 1, devices.enrollmentTokenId
        // (already set above) is the correct many-to-one lookup.
        if (token.maxUses == 1) {
            token.claimedByDevice = device
        }
        enrollmentTokenRepository.save(token)

        return DpcEnrollResponse(
            deviceId = device.id!!,
            deviceApiKey = credential.rawKey,
            checkInIntervalSeconds = checkInIntervalSeconds,
        )
    }
}
