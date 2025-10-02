package no.nav.emottak.eventmanager.model

import org.jetbrains.exposed.sql.SortOrder

const val ASCENDING = "ASC"
const val DESCENDING = "DESC"

class Pageable( // Based on Spring Data Pageable
    val pageNumber: Int = 1,
    val pageSize: Int,
    val sort: String = ASCENDING
) {
    val offset = (pageNumber - 1) * pageSize.toLong()

    fun next() = Pageable(pageNumber + 1, pageSize, sort)

    fun getSortOrder(): SortOrder {
        if (sort.startsWith(DESCENDING, true)) return SortOrder.DESC
        return SortOrder.ASC
    }
}
