package com.primeos.mdm.policy

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

// Bridges Policy.definition (raw JSON text in the jsonb column) and the
// typed PolicyDefinition model. Kept separate from the entity itself so the
// entity stays a plain JPA/Hibernate concern and this stays a plain
// Jackson concern.
@Component
class PolicyDefinitionCodec(private val objectMapper: ObjectMapper) {

    fun decode(json: String): PolicyDefinition = objectMapper.readValue(json, PolicyDefinition::class.java)

    fun encode(definition: PolicyDefinition): String = objectMapper.writeValueAsString(definition)
}
