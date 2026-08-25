package com.primeos.mdm.enterprise

import com.fasterxml.jackson.databind.ObjectMapper
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class GmsNotificationProcessor(
    private val gmsNotificationEventRepository: GmsNotificationEventRepository,
    private val deviceRepository: DeviceRepository,
    private val commandRepository: CommandRepository,
    private val objectMapper: ObjectMapper,
) {

    // Always persists the raw payload, whether or not structured
    // interpretation succeeds - see GmsPubSubNotification for why that
    // interpretation isn't fully verified yet. A parse/handling failure
    // is recorded on the row rather than thrown, so one malformed message
    // doesn't get endlessly redelivered by the Pub/Sub listener's nack.
    @Transactional
    fun process(rawJson: String) {
        val event = GmsNotificationEvent(rawPayload = rawJson)

        try {
            val notification = objectMapper.readValue(rawJson, GmsPubSubNotification::class.java)
            event.notificationType = notification.notificationType
            handle(notification)
            event.processedAt = Instant.now()
        } catch (ex: Exception) {
            event.processingError = ex.message ?: ex.javaClass.simpleName
        }

        gmsNotificationEventRepository.save(event)
    }

    private fun handle(notification: GmsPubSubNotification) {
        when (notification.notificationType) {
            "STATUS_REPORT", "ENROLLMENT", "COMPLIANCE_REPORT" -> handleDeviceStateNotification(notification)
            "COMMAND" -> handleCommandNotification(notification)
            else -> Unit // TEST, USAGE_LOGS, or anything unrecognized: raw payload is already stored above
        }
    }

    private fun handleDeviceStateNotification(notification: GmsPubSubNotification) {
        val deviceName = notification.device ?: return
        val device = deviceRepository.findByGmsDeviceResourceName(deviceName) ?: return

        device.lastSeenAt = Instant.now()
        if (device.status == DeviceStatus.PROVISIONING) {
            device.status = DeviceStatus.ACTIVE
        }
        deviceRepository.save(device)
    }

    // Best-effort: the notification tells us which device and (usually)
    // the command payload, but not the exact operation name we stored
    // from issueCommand's response - so this reconciles against the most
    // recently dispatched still-in-flight command for that device, rather
    // than an exact id match. Verify this assumption once a real device
    // exists to test against.
    private fun handleCommandNotification(notification: GmsPubSubNotification) {
        val deviceName = notification.device ?: return
        val device = deviceRepository.findByGmsDeviceResourceName(deviceName) ?: return

        val inFlight = commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.SENT)
        val command = inFlight.maxByOrNull { it.createdAt ?: Instant.MIN } ?: return

        val errorCode = notification.command?.get("errorCode")
        command.status = if (errorCode != null) CommandStatus.FAILED else CommandStatus.COMPLETED
        command.errorMessage = errorCode?.toString()
        command.completedAt = Instant.now()
        commandRepository.save(command)
    }
}
