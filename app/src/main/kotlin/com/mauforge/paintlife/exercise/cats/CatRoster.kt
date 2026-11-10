package com.mauforge.paintlife.exercise.cats

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import com.mauforge.paintlife.R

/**
 * Display content for one Cat League opponent. Progress (unlocked, beaten, benchmark) lives in
 * the database keyed by [key]; this is the single place that maps a cat to its art and words.
 *
 * Art: [avatarRes] is `R.drawable.cat_<key>`, a 640×640 WebP in res/drawable-nodpi with a gold
 * ring baked into the picture. Replacing a picture means replacing that file only. See README.
 */
@Immutable
data class CatProfile(
    val level: Int,
    val key: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val breedRes: Int,
    @param:StringRes val personalityRes: Int,
    @param:DrawableRes val avatarRes: Int,
    /** Where the circular crop centres on the photo; adjust per cat if a face is off-centre. */
    val photoAlignment: Alignment = Alignment.Center,
)

object CatRoster {
    val cats: List<CatProfile> = listOf(
        CatProfile(1, "aero", R.string.cat_aero_name, R.string.cat_aero_breed, R.string.cat_aero_personality, R.drawable.cat_aero),
        CatProfile(2, "aria", R.string.cat_aria_name, R.string.cat_aria_breed, R.string.cat_aria_personality, R.drawable.cat_aria),
        CatProfile(3, "pepper", R.string.cat_pepper_name, R.string.cat_pepper_breed, R.string.cat_pepper_personality, R.drawable.cat_pepper),
        CatProfile(4, "coco", R.string.cat_coco_name, R.string.cat_coco_breed, R.string.cat_coco_personality, R.drawable.cat_coco),
        CatProfile(5, "luna", R.string.cat_luna_name, R.string.cat_luna_breed, R.string.cat_luna_personality, R.drawable.cat_luna),
        CatProfile(6, "simba", R.string.cat_simba_name, R.string.cat_simba_breed, R.string.cat_simba_personality, R.drawable.cat_simba),
        CatProfile(7, "cloud", R.string.cat_cloud_name, R.string.cat_cloud_breed, R.string.cat_cloud_personality, R.drawable.cat_cloud),
        CatProfile(8, "titan", R.string.cat_titan_name, R.string.cat_titan_breed, R.string.cat_titan_personality, R.drawable.cat_titan),
        CatProfile(9, "olive", R.string.cat_olive_name, R.string.cat_olive_breed, R.string.cat_olive_personality, R.drawable.cat_olive),
        CatProfile(10, "milo", R.string.cat_milo_name, R.string.cat_milo_breed, R.string.cat_milo_personality, R.drawable.cat_milo),
    )

    private val byKey = cats.associateBy { it.key }
    private val byLevel = cats.associateBy { it.level }

    fun forKey(key: String): CatProfile? = byKey[key]

    fun forLevel(level: Int): CatProfile? = byLevel[level]
}
