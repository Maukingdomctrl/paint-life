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
 * Art: [avatarRes] is `R.drawable.cat_<key>`. Until the owner's photos arrive each one is an
 * alias of the temporary placeholder (res/values/cat_art_<key>.xml). Importing a photo replaces
 * that alias file with res/drawable-nodpi/cat_<key>.webp; no code changes. See README.
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
        CatProfile(1, "ana", R.string.cat_ana_name, R.string.cat_ana_breed, R.string.cat_ana_personality, R.drawable.cat_ana),
        CatProfile(2, "mia", R.string.cat_mia_name, R.string.cat_mia_breed, R.string.cat_mia_personality, R.drawable.cat_mia),
        CatProfile(3, "zoe", R.string.cat_zoe_name, R.string.cat_zoe_breed, R.string.cat_zoe_personality, R.drawable.cat_zoe),
        CatProfile(4, "eva", R.string.cat_eva_name, R.string.cat_eva_breed, R.string.cat_eva_personality, R.drawable.cat_eva),
        CatProfile(5, "luna", R.string.cat_luna_name, R.string.cat_luna_breed, R.string.cat_luna_personality, R.drawable.cat_luna),
        CatProfile(6, "sara", R.string.cat_sara_name, R.string.cat_sara_breed, R.string.cat_sara_personality, R.drawable.cat_sara),
        CatProfile(7, "nina", R.string.cat_nina_name, R.string.cat_nina_breed, R.string.cat_nina_personality, R.drawable.cat_nina),
        CatProfile(8, "maya", R.string.cat_maya_name, R.string.cat_maya_breed, R.string.cat_maya_personality, R.drawable.cat_maya),
        CatProfile(9, "aria", R.string.cat_aria_name, R.string.cat_aria_breed, R.string.cat_aria_personality, R.drawable.cat_aria),
        CatProfile(10, "champion", R.string.cat_champion_name, R.string.cat_champion_breed, R.string.cat_champion_personality, R.drawable.cat_champion),
    )

    private val byKey = cats.associateBy { it.key }
    private val byLevel = cats.associateBy { it.level }

    fun forKey(key: String): CatProfile? = byKey[key]

    fun forLevel(level: Int): CatProfile? = byLevel[level]
}
