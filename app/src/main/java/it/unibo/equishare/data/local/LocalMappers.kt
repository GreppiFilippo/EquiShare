/** Maps local cache entities into domain models. */
package it.unibo.equishare.data.local

import it.unibo.equishare.data.remote.dto.ExpenseCategoryDto
import it.unibo.equishare.data.remote.dto.ExpenseDto
import it.unibo.equishare.data.remote.dto.GroupCategoryDto
import it.unibo.equishare.data.remote.dto.ProfileDto
import it.unibo.equishare.data.remote.dto.UserGroupRow
import it.unibo.equishare.data.remote.mappers.parseOffsetDateTime
import it.unibo.equishare.data.remote.mappers.resolvedExpenseCategoryName
import it.unibo.equishare.data.remote.mappers.resolvedExpenseCategoryNameEn
import it.unibo.equishare.data.remote.mappers.resolvedExpenseCategoryNameIt
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.ActivityKind
import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.ExpenseStatus
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupCategory
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.GroupSettings
import it.unibo.equishare.domain.model.GroupType
import it.unibo.equishare.domain.model.MemberRole
import it.unibo.equishare.domain.model.LanguageCode
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.SplitMethod

private fun buildTranslationMap(it: String?, en: String?): Map<String, String> = buildMap {
    if (!it.isNullOrBlank()) put(LanguageCode.IT, it)
    if (!en.isNullOrBlank()) put(LanguageCode.EN, en)
}

fun UserGroupRow.toEntity(ownerUserId: String, cachedAt: String): CachedGroupEntity =
    CachedGroupEntity(
        ownerUserId = ownerUserId,
        id = id,
        name = name,
        description = description,
        type = type,
        avatarUrl = avatarUrl,
        baseCurrency = baseCurrency,
        role = role,
        joinedAt = joinedAt,
        memberCount = memberCount,
        balance = balance,
        categoryId = categoryId,
        categoryCode = categoryCode,
        categoryNameIt = categoryNameIt,
        categoryNameEn = categoryNameEn,
        categoryIconKey = categoryIconKey,
        isFavorite = isFavorite,
        cachedAt = cachedAt,
    )

fun CachedGroupEntity.toDomain(): Group {
    val groupType = GroupType.fromDb(type)
    val currency = Currency.fromCode(baseCurrency)
    return Group(
        id = id,
        name = name,
        description = description,
        type = groupType,
        category = categoryIconKey?.let(GroupCategory.Companion::fromKey)
            ?: GroupCategory.fromGroupType(groupType),
        avatarUrl = avatarUrl,
        baseCurrency = currency,
        currentUserRole = MemberRole.fromDb(role),
        memberCount = memberCount,
        balance = Money.of(balance, currency),
        isFavorite = isFavorite,
    )
}

fun CachedGroupEntity.toSettings(): GroupSettings {
    val currency = Currency.fromCode(baseCurrency)
    return GroupSettings(
        name = name,
        description = description,
        avatarUrl = avatarUrl,
        currentUserRole = MemberRole.fromDb(role),
        memberCount = memberCount,
        currentUserBalance = Money.of(balance, currency),
    )
}

fun GroupMember.toEntity(ownerUserId: String, groupId: String, cachedAt: String): CachedGroupMemberEntity =
    CachedGroupMemberEntity(
        ownerUserId = ownerUserId,
        groupId = groupId,
        userId = userId,
        role = role.dbValue,
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
        isCurrentUser = isCurrentUser,
        cachedAt = cachedAt,
    )

fun CachedGroupMemberEntity.toDomain(): GroupMember =
    GroupMember(
        userId = userId,
        role = MemberRole.fromDb(role),
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
        isCurrentUser = isCurrentUser,
    )

fun GroupCategoryDto.toEntity(cachedAt: String): CachedGroupCategoryEntity =
    CachedGroupCategoryEntity(
        id = id,
        code = code,
        nameIt = nameIt,
        nameEn = nameEn,
        iconKey = iconKey,
        groupType = groupType,
        sortOrder = sortOrder,
        cachedAt = cachedAt,
    )

fun CachedGroupCategoryEntity.toDomain(): AppCategory =
    AppCategory(
        id = id,
        code = code,
        translations = buildTranslationMap(nameIt, nameEn),
        iconKey = iconKey,
        groupType = GroupType.fromDb(groupType),
        sortOrder = sortOrder,
    )

fun ExpenseDto.toEntity(ownerUserId: String, cachedAt: String): CachedExpenseEntity =
    CachedExpenseEntity(
        ownerUserId = ownerUserId,
        id = id.orEmpty(),
        groupId = groupId.orEmpty(),
        title = title,
        description = description,
        categoryId = categoryId,
        expenseDate = expenseDate,
        currency = currency,
        totalAmount = totalAmount,
        paidByUserId = paidByUserId,
        splitMethod = splitMethod,
        status = status,
        receiptUrl = receiptUrl,
        createdBy = createdBy,
        createdAt = createdAt,
        cachedAt = cachedAt,
    )

fun CachedExpenseEntity.toDomain(): Expense {
    val currencyValue = Currency.fromCode(currency)
    return Expense(
        id = id,
        groupId = groupId,
        title = title,
        description = description,
        categoryId = categoryId,
        expenseDate = expenseDate,
        total = Money.of(totalAmount, currencyValue),
        paidByUserId = paidByUserId,
        payerUserIds = emptyList(),
        splitMethod = SplitMethod.fromDb(splitMethod),
        status = ExpenseStatus.fromDb(status),
        receiptUrl = receiptUrl,
        createdBy = createdBy,
        createdAt = createdAt,
    )
}

fun ExpenseCategoryDto.toEntity(cachedAt: String): CachedExpenseCategoryEntity =
    resolvedExpenseCategoryName().let { fallbackName ->
        CachedExpenseCategoryEntity(
            id = id,
            code = code,
            name = fallbackName,
            nameIt = resolvedExpenseCategoryNameIt(fallbackName),
            nameEn = resolvedExpenseCategoryNameEn(fallbackName),
            icon = icon,
            sortOrder = sortOrder,
            cachedAt = cachedAt,
        )
    }

fun CachedExpenseCategoryEntity.toDomain(): ExpenseCategory =
    ExpenseCategory(
        id = id,
        name = name,
        code = code,
        iconKey = icon,
        translations = buildTranslationMap(nameIt, nameEn),
    )

fun ActivityEntry.toEntity(ownerUserId: String, cachedAt: String): CachedActivityEntity =
    CachedActivityEntity(
        ownerUserId = ownerUserId,
        id = id,
        kind = kind.dbValue,
        createdAt = createdAt.toString(),
        groupId = groupId,
        expenseId = expenseId,
        paymentId = paymentId,
        groupName = groupName,
        groupCategoryKey = groupCategory.iconKey,
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName,
        amount = amount?.toDouble(),
        amountCurrency = amount?.currency?.code,
        isActorCurrentUser = isActorCurrentUser,
        isTargetCurrentUser = isTargetCurrentUser,
        cachedAt = cachedAt,
    )

fun CachedActivityEntity.toDomain(): ActivityEntry {
    val currency = Currency.fromCode(amountCurrency)
    return ActivityEntry(
        id = id,
        kind = ActivityKind.fromDb(kind),
        createdAt = parseOffsetDateTime(createdAt),
        groupId = groupId,
        expenseId = expenseId,
        paymentId = paymentId,
        expenseTitle = null,
        groupName = groupName,
        groupCategory = GroupCategory.fromKey(groupCategoryKey),
        actorUserId = actorUserId,
        actorDisplayName = actorDisplayName,
        targetUserId = targetUserId,
        targetDisplayName = targetDisplayName,
        amount = amount?.let { Money.of(it, currency) },
        isActorCurrentUser = isActorCurrentUser,
        isTargetCurrentUser = isTargetCurrentUser,
    )
}

fun ProfileDto.toEntity(cachedAt: String): CachedProfileEntity =
    CachedProfileEntity(
        id = id,
        email = email,
        fullName = fullName,
        avatarUrl = avatarUrl,
        defaultCurrency = defaultCurrency,
        locale = locale,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cachedAt = cachedAt,
    )

fun CachedProfileEntity.toDto(): ProfileDto =
    ProfileDto(
        id = id,
        email = email,
        fullName = fullName,
        avatarUrl = avatarUrl,
        defaultCurrency = defaultCurrency,
        locale = locale,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
