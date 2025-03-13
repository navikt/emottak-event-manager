package no.nav.emottak.eventmanager.configuration

data class Config(
    val environment: Environment,
    val database: Database
)

data class Environment(
    val naisClusterName: NaisClusterName
)

data class Database(
    val vaultJdbcUrl: VaultJdbcUrl,
    val maxConnectionPoolSizeForUser: MaxConnectionPoolSizeForUser,
    val maxConnectionPoolSizeForAdmin: MaxConnectionPoolSizeForAdmin
)

@JvmInline
value class NaisClusterName(val value: String)

@JvmInline
value class VaultJdbcUrl(val value: String)

@JvmInline
value class MaxConnectionPoolSizeForUser(val value: Int)

@JvmInline
value class MaxConnectionPoolSizeForAdmin(val value: Int)
