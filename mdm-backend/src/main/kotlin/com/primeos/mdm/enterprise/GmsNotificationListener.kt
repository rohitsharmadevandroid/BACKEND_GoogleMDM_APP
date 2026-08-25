package com.primeos.mdm.enterprise

import com.google.cloud.spring.pubsub.core.PubSubTemplate
import com.google.cloud.pubsub.v1.Subscriber
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// Pulls from a subscription on the shared GMS notification topic (see the
// gcloud setup this pairs with) and hands each message to
// GmsNotificationProcessor. Ack/nack is based on whether processing threw,
// not on whether structured interpretation succeeded - the processor
// itself treats a parse failure as "store it and move on" rather than an
// error, since redelivering a message we can't parse wouldn't fix anything.
@Component
class GmsNotificationListener(
    private val pubSubTemplate: PubSubTemplate,
    private val gmsNotificationProcessor: GmsNotificationProcessor,
    @Value("\${mdm.gcp.pubsub-subscription-id:}")
    private val subscriptionId: String,
) {

    private val logger = LoggerFactory.getLogger(GmsNotificationListener::class.java)
    private var subscriber: Subscriber? = null

    @PostConstruct
    fun start() {
        if (subscriptionId.isBlank()) {
            logger.warn("mdm.gcp.pubsub-subscription-id not set - GMS notification listener not started")
            return
        }

        subscriber = pubSubTemplate.subscribe(subscriptionId) { message ->
            try {
                gmsNotificationProcessor.process(message.pubsubMessage.data.toStringUtf8())
                message.ack()
            } catch (ex: Exception) {
                logger.error("Failed to process GMS Pub/Sub notification", ex)
                message.nack()
            }
        }
    }

    @PreDestroy
    fun stop() {
        subscriber?.takeIf { it.isRunning }?.stopAsync()
    }
}
