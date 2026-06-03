package no.nav.emottak.eventmanager.configuration

import no.nav.emottak.utils.config.Kafka
import no.nav.emottak.utils.config.Server
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG
import org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG
import java.util.Properties

data class Config(
    val environment: Environment,
    val database: Database,
    val kafka: Kafka,
    val eventConsumer: EventConsumer,
    val server: Server,
    val delayedJobs: DelayedJobs,
    // HopLite leser miljøvariablene NAV_PARTY_IDS_0, NAV_PARTY_IDS_1, osv og gjenkjenner dette som en liste: NAV_PARTY_IDS.
    // Disse er definert i nais-console => configs => emottak-event-manager-config.
    val navPartyIds: List<String> = emptyList()
)

data class Environment(
    val naisClusterName: NaisClusterName
)

data class Database(
    val vaultJdbcUrl: VaultJdbcUrl,
    val dbCredentialsMountPath: DbCredentialsMountPath,
    val maxConnectionPoolSizeForUser: MaxConnectionPoolSizeForUser,
    val maxConnectionPoolSizeForAdmin: MaxConnectionPoolSizeForAdmin
)

data class EventConsumer(
    val active: Boolean,
    val eventTopic: String,
    val messageDetailsTopic: String,
    val consumerGroupId: String
)

data class DelayedJobs(
    val delayInMinutes: Int
)

@JvmInline
value class NaisClusterName(val value: String)

@JvmInline
value class VaultJdbcUrl(val value: String)

@JvmInline
value class DbCredentialsMountPath(val value: String)

@JvmInline
value class MaxConnectionPoolSizeForUser(val value: Int)

@JvmInline
value class MaxConnectionPoolSizeForAdmin(val value: Int)

fun Kafka.toProperties() = Properties()
    .apply {
        put(SECURITY_PROTOCOL_CONFIG, securityProtocol.value)
        put(SSL_KEYSTORE_TYPE_CONFIG, keystoreType.value)
        put(SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation.value)
        put(SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword.value)
        put(SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType.value)
        put(SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation.value)
        put(SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword.value)
    }
