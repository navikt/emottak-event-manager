package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

// Based on Spring Data's Page implementation
@Serializable
data class Page<T> (
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val content: List<T>
) {
    val totalPages: Int? =
        if (totalElements == 0L) {
            0
        } else if (totalElements % size == 0L) {
            (totalElements / size).toInt()
        } else {
            ((totalElements / size) + 1).toInt()
        }
}
