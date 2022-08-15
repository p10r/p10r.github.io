package de.p10r

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RecordDealStatusUpdateService(
    private val dealRepository: DealRepository,
    private val dealStatusUpdatedEventHandler: DealStatusUpdatedEventHandler,
    private val retrieveDealApiClient: CoxRetrieveDealApiClient,
) {

    fun record(dealStatusUpdate: DealStatusUpdate): DealStatusUpdatedEvent? =
        dealStatusUpdate.updateCorrespondingDeal()

    private fun DealStatusUpdate.updateCorrespondingDeal(): DealStatusUpdatedEvent? {
        val deal = findCorrespondingDeal() ?: return null

        val updatedDeal = deal.updateStatus(
            status = status,
            conditions = conditions,
            statusUpdatedAt = statusUpdated.toLocalDateTime()
        )

        val event = DealStatusUpdatedEvent.of(deal, updatedDeal)

        dealRepository.save(updatedDeal)
        return dealStatusUpdatedEventHandler.handle(event)
    }

    private fun DealStatusUpdate.findCorrespondingDeal() = dealRepository.findById(dealId).also {
        if (it == null)
            println("Could not find deal with id ${dealId}. Skipping status update.")
    }
}

fun DealStatusUpdatedEvent.Companion.of(
    oldDeal: Deal,
    updatedDeal: Deal,
) = DealStatusUpdatedEvent(
    deal = updatedDeal,
    oldStatus = oldDeal.currentStatusDetails.status,
    newStatus = updatedDeal.currentStatusDetails.status,
    statusUpdatedAt = updatedDeal.currentStatusDetails.statusUpdatedAt.toInstant(ZoneOffset.UTC)
)

interface DealRepository {
    fun findById(id: String): Deal?
    fun save(deal: Deal): Deal
}

interface DealStatusUpdatedEventHandler {
    fun handle(event: DealStatusUpdatedEvent): DealStatusUpdatedEvent
}

interface CoxRetrieveDealApiClient {
    fun getDealInfo(dealId: String)
}

data class DealStatusUpdatedEvent(
    val deal: Deal,
    val oldStatus: String,
    val newStatus: String,
    val statusUpdatedAt: Instant,
) {
    companion object
}

data class Deal(
    val id: String,
    val statusHistory: StatusHistory,
) {
    companion object;

    @Suppress("SimplifiableCallChain")
    val currentStatusDetails: StatusDetails = statusHistory.sortedBy { it.statusUpdatedAt }.last()

    fun updateStatus(
        status: String,
        conditions: StatusDetails.Conditions?,
        statusUpdatedAt: LocalDateTime = LocalDateTime.now(),
    ) = copy(statusHistory = statusHistory.updateStatus(status, statusUpdatedAt, conditions))

    data class StatusDetails(
        val status: String,
        val statusUpdatedAt: LocalDateTime,
        val conditions: Conditions?,
    ) {
        data class Conditions(val term: Int?)
    }

    data class StatusHistory(
        val status: List<StatusDetails>,
    ) : List<StatusDetails> by status {
        fun updateStatus(
            status: String,
            statusUpdatedAt: LocalDateTime,
            conditions: StatusDetails.Conditions?,
        ) = StatusHistory(
            status = (this.status + StatusDetails(status, statusUpdatedAt, conditions))
                .sortedBy { it.statusUpdatedAt }
        )
    }
}

data class DealStatusUpdate(
    val dealId: String,
    val status: String,
    val statusUpdated: OffsetDateTime,
    val conditions: Deal.StatusDetails.Conditions?
)
