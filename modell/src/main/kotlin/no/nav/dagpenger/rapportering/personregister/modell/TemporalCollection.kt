package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TreeMap

// Temporal object pattern from https://martinfowler.com/eaaDev/TemporalObject.html
class TemporalCollection<R> {
    private val contents = TreeMap<LocalDateTime, R>()
    private val milestones get() = contents.keys.toList().reversed()

    @Synchronized
    fun get(date: LocalDateTime): R =
        milestones
            .firstOrNull { it.isBefore(date) || it.isEqual(date) }
            ?.let { contents[it] }
            ?: throw IllegalArgumentException("No records that early. Milestones=$milestones")

    @Synchronized
    fun get(date: LocalDate): R = get(date.atStartOfDay())

    @Synchronized
    fun put(
        at: LocalDateTime,
        item: R,
    ) {
        contents[at] = item
    }

    @Synchronized
    fun put(
        at: LocalDate,
        item: R,
    ) {
        put(at.atStartOfDay(), item)
    }

    @Synchronized
    fun isEmpty(): Boolean = contents.isEmpty()

    @Synchronized
    fun getAll(): List<Pair<LocalDateTime, R>> = contents.toList()
}
