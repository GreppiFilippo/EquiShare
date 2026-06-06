/** Implements the Create Group use case. */
package it.unibo.equishare.domain.usecase

import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.domain.repository.GroupsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class CreateGroupUseCase(
    private val groups: GroupsRepository,
) {
    data class Input(
        val name: String,
        val description: String?,
        val category: AppCategory,
        val photo: ImageUpload? = null,
    )

    suspend operator fun invoke(input: Input): Result<String> = runCatching {
        withContext(NonCancellable) {
            val groupId = groups.create(
                name = input.name,
                description = input.description?.takeIf { it.isNotBlank() },
                type = input.category.groupType,
                categoryId = input.category.id,
            )
            try {
                input.photo?.let { photo ->
                    groups.uploadGroupPhoto(groupId, photo.bytes, photo.mimeType).getOrThrow()
                }
            } catch (t: Throwable) {
                runCatching { groups.delete(groupId) }
                throw t
            }
            groupId
        }
    }
}
