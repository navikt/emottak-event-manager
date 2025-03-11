package no.nav.emottak.eventmanager.persistence

import com.bettercloud.vault.response.LogicalResponse
import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.eventmanager.config
import no.nav.emottak.eventmanager.log
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import no.nav.vault.jdbc.hikaricp.VaultUtil

const val EVENT_DB_NAME = "emottak-event-manager-db"

val eventDbConfig = lazy { VaultConfig().configure("user") }

val eventMigrationConfig = lazy { VaultConfig().configure("admin") }

data class VaultConfig(
    val databaseName: String = EVENT_DB_NAME,
    val jdbcUrl: String = config.database.vaultJdbcUrl.value.also {
        log.info("vault jdbc url set til: $it")
    },
    val vaultMountPath: String = (
        "postgresql/prod-fss".takeIf { config.environment.naisClusterName.value == "prod-fss" } ?: "postgresql/preprod-fss"
        )
        .also {
            log.info("vaultMountPath satt til $it")
        }
)

fun VaultConfig.configure(role: String): HikariConfig {
    val maxPoolSizeForUser = config.database.maxConnectionPoolSizeForUser.value
    val maxPoolSizeForAdmin = config.database.maxConnectionPoolSizeForAdmin.value

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = this@configure.jdbcUrl + databaseName
        driverClassName = "org.postgresql.Driver"
        this.maximumPoolSize = maxPoolSizeForUser
        if (role == "admin") {
            this.maximumPoolSize = maxPoolSizeForAdmin
            val vault = VaultUtil.getInstance().client
            val path: String = this@configure.vaultMountPath + "/creds/$databaseName-$role"
            log.info("Fetching database credentials for role admin")
            val response: LogicalResponse = vault.logical().read(path)
            this.username = response.data["username"]
            this.password = response.data["password"]
        }
    }

    if (role == "admin") {
        return hikariConfig
    }
    return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfig,
        this@configure.vaultMountPath,
        "$databaseName-$role"
    )
}
