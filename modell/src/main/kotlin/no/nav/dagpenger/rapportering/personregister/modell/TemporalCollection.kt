package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TreeMap

// Temporal object pattern from https://martinfowler.com/eaaDev/TemporalObject.html
class TemporalCollection<R> {
    private val contents = TreeMap<LocalDateTime, R>()
    private val milestones get() = contents.keys.toList().reversed()

    fun get(date: LocalDateTime): R =
        milestones
            .firstOrNull { it.isBefore(date) || it.isEqual(date) }
            ?.let {
                contents[it]
            } ?: throw IllegalArgumentException("No records that early. Milestones=$milestones")

    fun get(date: LocalDate): R = get(date.atStartOfDay())

    fun put(
        at: LocalDateTime,
        item: R,
    ) {
        contents[at] = item
    }

    fun put(
        at: LocalDate,
        item: R,
    ) {
        put(at.atStartOfDay(), item)
    }

    fun isNotEmpty() = contents.isNotEmpty()

    fun allItems(): List<Pair<LocalDateTime, R>> = contents.toList()
}
