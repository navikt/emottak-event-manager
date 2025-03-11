package no.nav.emottak.eventmanager.configuration

data class Config(
    val database: Database
)

data class Database(
    val vaultJdbcUrl: VaultJdbcUrl,
    val maxConnectionPoolSizeForUser: MaxConnectionPoolSizeForUser,
    val maxConnectionPoolSizeForAdmin: MaxConnectionPoolSizeForAdmin,
)

@JvmInline
value class VaultJdbcUrl(val value: String)

@JvmInline
value class MaxConnectionPoolSizeForUser(val value: Int)

@JvmInline
value class MaxConnectionPoolSizeForAdmin(val value: Int)
