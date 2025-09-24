package no.nav.emottak.eventmanager.model

class Pageable( // Based on Spring Data Pageable
    val pageNumber: Int,
    val pageSize: Int
) {
    val offset = (pageNumber - 1) * pageSize.toLong()

    fun next() = Pageable(pageNumber + 1, pageSize)
}
