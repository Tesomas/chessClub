package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "One page of results")
data class PageResponse<T>(
    @Schema(description = "Items on this page")
    val content: List<T>,

    @Schema(description = "0-based page index", example = "0")
    val page: Int,

    @Schema(description = "Requested page size", example = "25")
    val size: Int,

    @Schema(description = "Number of items matching the query across all pages", example = "140")
    val totalElements: Long,

    @Schema(description = "Number of pages for this page size (0 when nothing matches)", example = "6")
    val totalPages: Int
) {
    fun <R> map(transform: (T) -> R): PageResponse<R> =
        PageResponse(content.map(transform), page, size, totalElements, totalPages)

    companion object {
        fun <T> of(content: List<T>, page: Int, size: Int, totalElements: Long): PageResponse<T> =
            PageResponse(content, page, size, totalElements, pageCount(totalElements, size))

        private fun pageCount(totalElements: Long, size: Int): Int =
            ((totalElements + size - 1) / size).toInt()
    }
}
