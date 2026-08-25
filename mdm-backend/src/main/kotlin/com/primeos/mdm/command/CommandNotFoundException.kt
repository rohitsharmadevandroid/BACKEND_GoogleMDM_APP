package com.primeos.mdm.command

import java.util.UUID

class CommandNotFoundException(commandId: UUID) : RuntimeException("No command with id $commandId")
