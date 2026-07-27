package com.dlovel.plankton.data

object SampleData {
    val datasets = listOf(
        Dataset(
            id = "demo-1",
            name = "赣江采样 2024-01",
            description = "45 张图片",
            created_at = System.currentTimeMillis()
        ),
        Dataset(
            id = "demo-2",
            name = "鄱阳湖采样 2024-02",
            description = "12 张图片",
            created_at = System.currentTimeMillis()
        )
    )

    val images = listOf(
        PlanktonImage(
            id = "img-1",
            dataset_id = "demo-1",
            image_url = "",
            custom_name = "示例图片 1"
        ),
        PlanktonImage(
            id = "img-2",
            dataset_id = "demo-1",
            image_url = "",
            custom_name = "示例图片 2"
        )
    )

    val species = listOf(
        Species(
            id = "sp-1",
            name_cn = "钩枝蚤",
            name_latin = "Bosmina sp.",
            category = "枝角类"
        ),
        Species(
            id = "sp-2",
            name_cn = "真剑水蚤",
            name_latin = "Cyclops sp.",
            category = "桡足类"
        )
    )
}
