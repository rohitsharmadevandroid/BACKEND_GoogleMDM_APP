package com.primeos.mdm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MdmBackendApplication

fun main(args: Array<String>) {
    runApplication<MdmBackendApplication>(*args)
}
