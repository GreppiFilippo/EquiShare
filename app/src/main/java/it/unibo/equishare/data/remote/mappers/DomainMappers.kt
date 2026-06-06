/** Maps remote DTOs into domain models. */
package it.unibo.equishare.data.remote.mappers

import it.unibo.equishare.data.remote.dto.ActivityLogDto
import it.unibo.equishare.data.remote.dto.ExpenseCategoryDto
import it.unibo.equishare.data.remote.dto.ExpenseDto
import it.unibo.equishare.data.remote.dto.GroupCategoryDto
import it.unibo.equishare.data.remote.dto.UserGroupRow
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.ActivityKind
import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.ExpenseStatus
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupCategory
import it.unibo.equishare.domain.model.GroupType
import it.unibo.equishare.domain.model.MemberRole
import it.unibo.equishare.domain.model.LanguageCode
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.SplitMethod
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

fun UserGroupRow.toDomain(): Group {
    val groupType = GroupType.fromDb(type)
    val category = categoryIconKey?.let(GroupCategory.Companion::fromKey)
        ?: GroupCategory.fromGroupType(groupType)
    val currency = Currency.fromCode(baseCurrency)
    return Group(
        id = id,
        name = name,
        description = description,
        type = groupType,
        category = category,
        avatarUrl = avatarUrl,
        baseCurrency = currency,
        currentUserRole = MemberRole.fromDb(role),
        memberCount = memberCount,
        balance = Money.of(balance, currency),
        isFavorite = isFavorite,
    )
}

fun ExpenseDto.toDomain(payerUserIds: List<String> = emptyList()): Expense {
    val currencyValue = Currency.fromCode(currency)
    return Expense(
        id = id.orEmpty(),
        groupId = groupId.orEmpty(),
        title = title,
        description = description,
        categoryId = categoryId,
        expenseDate = expenseDate,
        total = Money.of(totalAmount, currencyValue),
        paidByUserId = paidByUserId,
        payerUserIds = payerUserIds,
        splitMethod = SplitMethod.fromDb(splitMethod),
        status = ExpenseStatus.fromDb(status),
        receiptUrl = receiptUrl,
        createdBy = createdBy,
        createdAt = createdAt,
    )
}

fun ExpenseCategoryDto.toDomain(): ExpenseCategory {
    val fallbackName = resolvedExpenseCategoryName()
    return ExpenseCategory(
        id = id,
        name = fallbackName,
        code = code,
        iconKey = icon,
        translations = buildMap {
            put(LanguageCode.IT, resolvedExpenseCategoryNameIt(fallbackName))
            put(LanguageCode.EN, resolvedExpenseCategoryNameEn(fallbackName))
        },
    )
}

fun ExpenseCategoryDto.resolvedExpenseCategoryName(): String =
    name.notBlankOrNull()
        ?: nameEn.notBlankOrNull()
        ?: nameIt.notBlankOrNull()
        ?: code

fun ExpenseCategoryDto.resolvedExpenseCategoryNameIt(fallbackName: String = resolvedExpenseCategoryName()): String =
    nameIt.notBlankOrNull() ?: fallbackName

fun ExpenseCategoryDto.resolvedExpenseCategoryNameEn(fallbackName: String = resolvedExpenseCategoryName()): String =
    nameEn.notBlankOrNull() ?: fallbackName

private fun String?.notBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

fun GroupCategoryDto.toDomain(): AppCategory = AppCategory(
    id = id,
    code = code,
    translations = buildMap {
        if (nameIt.isNotBlank()) put(LanguageCode.IT, nameIt)
        if (nameEn.isNotBlank()) put(LanguageCode.EN, nameEn)
    },
    iconKey = iconKey,
    groupType = GroupType.fromDb(groupType),
    sortOrder = sortOrder,
)

fun ActivityLogDto.toDomain(
    groupName: String?,
    groupIconKey: String?,
    groupType: String?,
    actorDisplayName: String?,
    targetDisplayName: String?,
    targetUserId: String?,
    currentUserId: String?,
): ActivityEntry {
    val metaObj = metadata as? JsonObject

    val resolvedGroupIconKey = groupIconKey ?: metaObj?.string("group_icon_key")
    val resolvedGroupType = groupType ?: metaObj?.string("group_type")
    val category = resolvedGroupIconKey?.let(GroupCategory.Companion::fromKey)
        ?: GroupType.fromDb(resolvedGroupType).let(GroupCategory.Companion::fromGroupType)

    val amount = metaObj?.string("amount")?.toDoubleOrNull()
    val currency = Currency.fromCode(metaObj?.string("currency"))
    val money = amount?.let { Money.of(it, currency) }

    return ActivityEntry(
        id = id,
        kind = ActivityKind.fromDb(activityType),
        createdAt = parseOffsetDateTime(createdAt),
        groupId = groupId,
        expenseId = expenseId,
        paymentId = paymentId,
        expenseTitle = metaObj?.string("expense_title"),
        groupName = groupName
            ?: metaObj?.string("group_name")
            ?: metaObj?.string("group_title"),
        groupCategory = category,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName
            ?: metaObj?.string("actor_name")
            ?: metaObj?.string("inviter_name")
            ?: metaObj?.string("invited_by_name"),
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName
            ?: metaObj?.string("target_name")
            ?: metaObj?.string("invitee_name")
            ?: metaObj?.string("invited_user_name"),
        amount = money,
        isActorCurrentUser = actorUserId != null && actorUserId == currentUserId,
        isTargetCurrentUser = targetUserId != null && targetUserId == currentUserId,
    )
}

fun JsonObject.string(key: String): String? =
    get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

// Lenient parser: Supabase returns RFC3339 with Z, but locally generated rows
// may omit the timezone suffix.
fun parseOffsetDateTime(iso: String): OffsetDateTime =
    runCatching { OffsetDateTime.parse(iso) }
        .recoverCatching { OffsetDateTime.parse(iso + "Z") }
        .getOrElse { OffsetDateTime.now() }
