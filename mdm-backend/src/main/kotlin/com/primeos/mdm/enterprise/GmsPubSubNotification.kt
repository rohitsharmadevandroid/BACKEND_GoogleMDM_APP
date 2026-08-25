package com.primeos.mdm.enterprise

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

// The exact shape of Android Management API's Pub/Sub notification JSON is
// NOT part of the generated Java client - verified: no model class exists
// for it, unlike every other payload used this session. This reflects
// long-stable public documentation, not a javap-checked contract, and is
// UNTESTED against a real notification since enterprise-binding is still
// blocked. ignoreUnknown so any field we didn't anticipate doesn't break
// parsing - the raw payload is stored regardless (see GmsNotificationEvent).
@JsonIgnoreProperties(ignoreUnknown = true)
data class GmsPubSubNotification(
    val notificationType: String? = null,
    val device: String? = null,
    val enterpriseId: String? = null,
    // Present on COMMAND notifications: the Command resource itself,
    // notably its errorCode if the command failed.
    val command: Map<String, Any?>? = null,
)
