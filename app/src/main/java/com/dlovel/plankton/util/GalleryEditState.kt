package com.dlovel.plankton.util

/** 清空或修改联想文本后，解除上一次已选中的稳定物种 ID。 */
fun speciesIdAfterQueryChange(
    previousQuery: String,
    nextQuery: String,
    selectedSpeciesId: String?
): String? = if (selectedSpeciesId != null && previousQuery != nextQuery) null else selectedSpeciesId
