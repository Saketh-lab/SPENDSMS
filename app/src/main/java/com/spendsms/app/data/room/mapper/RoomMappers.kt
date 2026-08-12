package com.spendsms.app.data.room.mapper

import com.spendsms.app.data.room.entity.CategoryEntity
import com.spendsms.app.data.room.entity.ParserMetadataEntity
import com.spendsms.app.data.room.entity.ScanStateEntity
import com.spendsms.app.data.room.entity.SubscriptionEntity
import com.spendsms.app.data.room.entity.TransactionEntity
import com.spendsms.app.data.room.entity.UserCorrectionEntity
import com.spendsms.app.domain.corrections.UserCorrection
import com.spendsms.app.domain.merchant.Merchant
import com.spendsms.app.domain.model.AnalysisPeriod
import com.spendsms.app.domain.model.Category
import com.spendsms.app.domain.model.CategoryId
import com.spendsms.app.domain.model.Confidence
import com.spendsms.app.domain.model.CorrectionField
import com.spendsms.app.domain.model.CorrectionId
import com.spendsms.app.domain.model.CurrencyCode
import com.spendsms.app.domain.model.DuplicateStatus
import com.spendsms.app.domain.model.EpochMillis
import com.spendsms.app.domain.model.MerchantKey
import com.spendsms.app.domain.model.Money
import com.spendsms.app.domain.model.ParserBundleStatus
import com.spendsms.app.domain.model.ParserMetadata
import com.spendsms.app.domain.model.ParserVersion
import com.spendsms.app.domain.model.PaymentMethod
import com.spendsms.app.domain.model.RulesVersion
import com.spendsms.app.domain.model.ScanId
import com.spendsms.app.domain.model.ScanState
import com.spendsms.app.domain.model.ScanStatus
import com.spendsms.app.domain.model.SubscriptionFrequency
import com.spendsms.app.domain.model.SubscriptionId
import com.spendsms.app.domain.model.SubscriptionStatus
import com.spendsms.app.domain.model.Transaction
import com.spendsms.app.domain.model.TransactionDirection
import com.spendsms.app.domain.model.TransactionFingerprint
import com.spendsms.app.domain.model.TransactionId
import com.spendsms.app.domain.model.TransferStatus
import com.spendsms.app.domain.subscriptions.Subscription

fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId.of(categoryId),
    name = name,
    isSystemCategory = isSystemCategory,
    sortOrder = sortOrder,
    createdAt = EpochMillis.of(createdAt),
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    categoryId = id.value,
    name = name,
    isSystemCategory = isSystemCategory,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMillis,
)

fun TransactionEntity.toDomain(): Transaction {
    val merchant = if (merchantKey != null && merchantDisplayName != null) {
        Merchant(
            key = MerchantKey.of(merchantKey),
            displayName = merchantDisplayName,
            rawNormalized = merchantRawNormalized,
        )
    } else {
        null
    }
    return Transaction(
        id = TransactionId.of(transactionId),
        sourceMessageHash = sourceMessageHash,
        fingerprint = TransactionFingerprint.of(transactionFingerprint),
        timestamp = EpochMillis.of(transactionTimestamp),
        amount = Money.ofMinorUnits(amountMinorUnits, CurrencyCode.of(currency)),
        merchant = merchant,
        institution = institution,
        maskedAccount = maskedAccount,
        referenceHash = referenceHash,
        direction = enumValueOf<TransactionDirection>(direction),
        paymentMethod = enumValueOf<PaymentMethod>(paymentMethod),
        categoryId = CategoryId.of(categoryId),
        confidence = Confidence.of(confidence),
        parserVersion = ParserVersion.of(parserVersion),
        duplicateStatus = enumValueOf<DuplicateStatus>(duplicateStatus),
        possibleDuplicateOf = possibleDuplicateOf?.let(TransactionId::of),
        transferStatus = enumValueOf<TransferStatus>(transferStatus),
        isUserConfirmed = isUserConfirmed,
        createdAt = EpochMillis.of(createdAt),
        updatedAt = EpochMillis.of(updatedAt),
    )
}

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    transactionId = id.value,
    sourceMessageHash = sourceMessageHash,
    transactionFingerprint = fingerprint.value,
    transactionTimestamp = timestamp.toEpochMillis,
    amountMinorUnits = amount.amountMinorUnits,
    currency = amount.currency.code,
    merchantRawNormalized = merchant?.rawNormalized,
    merchantDisplayName = merchant?.displayName,
    merchantKey = merchant?.key?.value,
    institution = institution,
    maskedAccount = maskedAccount,
    referenceHash = referenceHash,
    direction = direction.name,
    paymentMethod = paymentMethod.name,
    categoryId = categoryId.value,
    confidence = confidence.value,
    parserVersion = parserVersion.value,
    duplicateStatus = duplicateStatus.name,
    possibleDuplicateOf = possibleDuplicateOf?.value,
    transferStatus = transferStatus.name,
    isUserConfirmed = isUserConfirmed,
    createdAt = createdAt.toEpochMillis,
    updatedAt = updatedAt.toEpochMillis,
)

fun UserCorrectionEntity.toDomain(): UserCorrection = UserCorrection(
    id = CorrectionId.of(correctionId),
    transactionId = TransactionId.of(transactionId),
    field = enumValueOf<CorrectionField>(fieldName),
    oldValue = oldValue,
    newValue = newValue,
    applyToFuture = applyToFuture,
    merchantMatchKey = merchantMatchKey?.let(MerchantKey::of),
    createdAt = EpochMillis.of(createdAt),
    updatedAt = EpochMillis.of(updatedAt),
)

fun UserCorrection.toEntity(): UserCorrectionEntity = UserCorrectionEntity(
    correctionId = id.value,
    transactionId = transactionId.value,
    fieldName = field.name,
    oldValue = oldValue,
    newValue = newValue,
    applyToFuture = applyToFuture,
    merchantMatchKey = merchantMatchKey?.value,
    createdAt = createdAt.toEpochMillis,
    updatedAt = updatedAt.toEpochMillis,
)

fun SubscriptionEntity.toDomain(
    evidenceTransactionIds: List<String>,
): Subscription = Subscription(
    id = SubscriptionId.of(subscriptionId),
    merchantKey = MerchantKey.of(merchantKey),
    merchantDisplayName = merchantDisplayName,
    frequency = enumValueOf<SubscriptionFrequency>(frequency),
    estimatedAmount = estimatedAmountMinor?.let {
        Money.ofMinorUnits(it, CurrencyCode.of(currency))
    },
    currency = CurrencyCode.of(currency),
    lastPaymentDate = lastPaymentDate?.let(EpochMillis::of),
    estimatedNextDate = estimatedNextDate?.let(EpochMillis::of),
    confidence = Confidence.of(confidence),
    status = enumValueOf<SubscriptionStatus>(status),
    evidenceTransactionIds = evidenceTransactionIds.map(TransactionId::of),
    createdAt = EpochMillis.of(createdAt),
    updatedAt = EpochMillis.of(updatedAt),
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    subscriptionId = id.value,
    merchantKey = merchantKey.value,
    merchantDisplayName = merchantDisplayName,
    frequency = frequency.name,
    estimatedAmountMinor = estimatedAmount?.amountMinorUnits,
    currency = currency.code,
    lastPaymentDate = lastPaymentDate?.toEpochMillis,
    estimatedNextDate = estimatedNextDate?.toEpochMillis,
    confidence = confidence.value,
    status = status.name,
    createdAt = createdAt.toEpochMillis,
    updatedAt = updatedAt.toEpochMillis,
)

fun ScanStateEntity.toDomain(): ScanState = ScanState(
    id = ScanId.of(scanId),
    period = AnalysisPeriod(
        start = EpochMillis.of(startDate),
        end = EpochMillis.of(endDate),
    ),
    lastProcessedMessageId = lastProcessedMessageId,
    parserVersion = ParserVersion.of(parserVersion),
    status = enumValueOf<ScanStatus>(status),
    processedCount = processedCount,
    acceptedCount = acceptedCount,
    startedAt = EpochMillis.of(startedAt),
    completedAt = completedAt?.let(EpochMillis::of),
    updatedAt = EpochMillis.of(updatedAt),
)

fun ScanState.toEntity(): ScanStateEntity = ScanStateEntity(
    scanId = id.value,
    startDate = period.start.toEpochMillis,
    endDate = period.end.toEpochMillis,
    lastProcessedMessageId = lastProcessedMessageId,
    parserVersion = parserVersion.value,
    status = status.name,
    processedCount = processedCount,
    acceptedCount = acceptedCount,
    startedAt = startedAt.toEpochMillis,
    completedAt = completedAt?.toEpochMillis,
    updatedAt = updatedAt.toEpochMillis,
)

fun ParserMetadataEntity.toDomain(): ParserMetadata = ParserMetadata(
    parserVersion = ParserVersion.of(parserVersion),
    rulesVersion = RulesVersion.of(rulesVersion),
    schemaVersion = schemaVersion,
    checksum = checksum,
    installedAt = EpochMillis.of(installedAt),
    activatedAt = activatedAt?.let(EpochMillis::of),
    status = enumValueOf<ParserBundleStatus>(status),
)

fun ParserMetadata.toEntity(): ParserMetadataEntity = ParserMetadataEntity(
    parserVersion = parserVersion.value,
    rulesVersion = rulesVersion.value,
    schemaVersion = schemaVersion,
    checksum = checksum,
    installedAt = installedAt.toEpochMillis,
    activatedAt = activatedAt?.toEpochMillis,
    status = status.name,
)
