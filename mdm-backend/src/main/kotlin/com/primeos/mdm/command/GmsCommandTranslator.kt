package com.primeos.mdm.command

import com.google.api.services.androidmanagement.v1.model.ClearAppsDataParams
import com.google.api.services.androidmanagement.v1.model.Command as GoogleCommand
import com.google.api.services.androidmanagement.v1.model.RequestDeviceInfoParams
import com.google.api.services.androidmanagement.v1.model.WipeParams
import org.springframework.stereotype.Component

@Component
class GmsCommandTranslator {

    fun translate(type: CommandType, params: CommandParams): GoogleCommand {
        val command = GoogleCommand().setType(googleType(type))

        when (type) {
            CommandType.LOCK ->
                params.lockDurationSeconds?.let { command.setDuration("${it}s") }

            CommandType.RESET_PASSWORD -> {
                params.newPassword?.let { command.setNewPassword(it) }
                if (params.resetPasswordFlags.isNotEmpty()) {
                    command.setResetPasswordFlags(params.resetPasswordFlags)
                }
            }

            CommandType.WIPE ->
                command.setWipeParams(WipeParams().setWipeDataFlags(params.wipeDataFlags))

            CommandType.CLEAR_APP_DATA ->
                command.setClearAppsDataParams(ClearAppsDataParams().setPackageNames(params.clearAppsDataPackageNames))

            CommandType.REQUEST_DEVICE_INFO ->
                params.requestDeviceInfoType?.let { command.setRequestDeviceInfoParams(RequestDeviceInfoParams().setDeviceInfo(it)) }

            CommandType.REBOOT -> Unit // no extra params
        }

        return command
    }

    private fun googleType(type: CommandType): String = when (type) {
        CommandType.LOCK -> "LOCK"
        CommandType.WIPE -> "WIPE"
        CommandType.REBOOT -> "REBOOT"
        CommandType.RESET_PASSWORD -> "RESET_PASSWORD"
        CommandType.CLEAR_APP_DATA -> "CLEAR_APP_DATA"
        CommandType.REQUEST_DEVICE_INFO -> "REQUEST_DEVICE_INFO"
    }
}
