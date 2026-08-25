package com.primeos.mdm.dpc

import com.fasterxml.jackson.databind.ObjectMapper
import com.primeos.mdm.command.CommandParams
import com.primeos.mdm.command.CommandRepository
import com.primeos.mdm.command.CommandStatus
import com.primeos.mdm.device.Device
import com.primeos.mdm.device.DeviceRepository
import com.primeos.mdm.device.DeviceStatus
import com.primeos.mdm.policy.PolicyDefinitionCodec
import com.primeos.mdm.policy.translator.CustomDpcPolicyTranslator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DpcCheckInService(
    private val deviceRepository: DeviceRepository,
    private val commandRepository: CommandRepository,
    private val policyDefinitionCodec: PolicyDefinitionCodec,
    private val customDpcPolicyTranslator: CustomDpcPolicyTranslator,
    private val objectMapper: ObjectMapper,
    @Value("\${mdm.dpc.check-in-interval-seconds:60}")
    private val checkInIntervalSeconds: Long,
) {

    @Transactional
    fun checkIn(device: Device, request: DpcCheckInRequest): DpcCheckInResponse {
        request.osVersion?.let { device.osVersion = it }
        request.model?.let { device.model = it }
        request.manufacturer?.let { device.manufacturer = it }
        device.lastSeenAt = Instant.now()
        if (device.status == DeviceStatus.PROVISIONING) {
            device.status = DeviceStatus.ACTIVE
        }
        deviceRepository.save(device)

        val policy = device.policy
        val policyChanged = policy != null && policy.version != request.lastPolicyVersionApplied
        val policyPayload = if (policyChanged) {
            customDpcPolicyTranslator.translate(policyDefinitionCodec.decode(policy!!.definition))
        } else {
            null
        }

        val pendingCommands = commandRepository.findByDeviceIdAndStatus(device.id!!, CommandStatus.PENDING)
        val dispatchedAt = Instant.now()
        pendingCommands.forEach {
            it.status = CommandStatus.SENT
            it.dispatchedAt = dispatchedAt
        }
        commandRepository.saveAll(pendingCommands)

        return DpcCheckInResponse(
            policy = policyPayload,
            policyVersion = policy?.version,
            pendingCommands = pendingCommands.map {
                DpcPendingCommand(
                    commandId = it.id!!,
                    type = it.commandType,
                    params = objectMapper.readValue(it.payload, CommandParams::class.java),
                )
            },
            checkInIntervalSeconds = checkInIntervalSeconds,
        )
    }
}
