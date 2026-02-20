package no.nav.emottak.eventmanager.persistence.repository

import no.nav.emottak.eventmanager.model.Pageable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import java.time.Instant

internal fun Query.applyPatternFilter(
    pattern: String = "",
    patternColumn: Column<String?>? = null
) {
    if (patternColumn != null && pattern.isNotBlank()) this.andWhere { patternColumn.lowerCase() like "%$pattern%".lowercase() }
}

internal fun Query.applyFilter(
    value: String = "",
    column: Column<String?>? = null
) {
    if (column != null && value.isNotBlank()) this.andWhere { column.lowerCase() like "%$value%".lowercase() }
}

internal fun Query.applyPagableLimitAndOrderBy(pageable: Pageable?, orderByColumn: Column<Instant>, defaultSortOrder: SortOrder = SortOrder.DESC) {
    if (pageable != null) {
        this.limit(pageable.pageSize)
            .offset(pageable.offset)
            .orderBy(orderByColumn, pageable.getSortOrder())
    } else {
        this.orderBy(orderByColumn, defaultSortOrder)
    }
}

internal fun Query.applyDatetimeFilter(
    dateColumn: Column<Instant>,
    from: Instant? = null,
    to: Instant? = null
) {
    if (from != null && to != null) {
        this.andWhere { dateColumn.between(from, to) }
    } else if (from != null) {
        this.andWhere { dateColumn.greaterEq(from) }
    } else if (to != null) {
        this.andWhere { dateColumn.lessEq(to) }
    }
}
