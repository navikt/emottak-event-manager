package no.nav.emottak.eventmanager.configuration

import no.nav.emottak.utils.config.Masked
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
    val eventConsumer: EventConsumer
)

data class Environment(
    val naisClusterName: NaisClusterName
)

data class Database(
    val vaultJdbcUrl: VaultJdbcUrl,
    val maxConnectionPoolSizeForUser: MaxConnectionPoolSizeForUser,
    val maxConnectionPoolSizeForAdmin: MaxConnectionPoolSizeForAdmin
)

data class Kafka(
    val bootstrapServers: String,
    val securityProtocol: SecurityProtocol,
    val keystoreType: KeystoreType,
    val keystoreLocation: KeystoreLocation,
    val keystorePassword: Masked,
    val truststoreType: TruststoreType,
    val truststoreLocation: TruststoreLocation,
    val truststorePassword: Masked
)

data class EventConsumer(
    val active: Boolean,
    val eventTopic: String,
    val consumerGroupId: String
)

@JvmInline
value class NaisClusterName(val value: String)

@JvmInline
value class VaultJdbcUrl(val value: String)

@JvmInline
value class MaxConnectionPoolSizeForUser(val value: Int)

@JvmInline
value class MaxConnectionPoolSizeForAdmin(val value: Int)

@JvmInline
value class SecurityProtocol(val value: String)

@JvmInline
value class KeystoreType(val value: String)

@JvmInline
value class KeystoreLocation(val value: String)

@JvmInline
value class TruststoreType(val value: String)

@JvmInline
value class TruststoreLocation(val value: String)

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
