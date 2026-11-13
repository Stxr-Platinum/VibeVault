package com.vibevault.app.ui.immersive.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import com.vibevault.app.ui.navigation.Screen
import com.vibevault.app.ui.theme.rememberReduceMotion

/**
 * Motion constants ported from `myspoti`'s `ImmersiveMotion.kt`.
 */
object ImmersiveMotion {
    fun <T> placementSpring() = spring<T>(stiffness = Spring.StiffnessMediumLow)

    fun <T> chromeSpring(reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) snap() else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    const val FADE_MS = 150
    const val MEDIUM_MS = 320
    const val LONG_MS = 420
    const val TINT_MS = 600
    const val SHEET_TINT_MS = 400

    fun <T> pressSpring() = spring<T>(stiffness = 900f)

    fun <T> glassSpring() = spring<T>(dampingRatio = 0.72f, stiffness = 320f)

    fun <T> releaseSpring() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)

    fun <T> sizeSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun slideSpring(reduce: Boolean = false): androidx.compose.animation.core.AnimationSpec<Float> =
        if (reduce) snap()
        else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 195f, visibilityThreshold = 0.5f)

    private const val PAGE_MS = 380

    object Pages {
        private val tabRoutes = setOf(
            Screen.Home.route,
            Screen.Search.route,
            Screen.Explore.route,
            Screen.Library.route
        )

        private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
            initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

        fun enter(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            val still = reduce || isTabSwitch()
            PageFade.enterMs = if (still) FADE_MS else PAGE_MS / 2
            if (still) fadeIn(tween(FADE_MS))
            else slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))) { it }
        }

        fun exit(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            val still = reduce || isTabSwitch()
            PageFade.exitMs = if (still) FADE_MS else PAGE_MS
            if (still) ExitTransition.None else slideOutHorizontally(tween(PAGE_MS)) { -it / 3 }
        }

        fun popEnter(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
            val still = reduce || isTabSwitch()
            PageFade.enterMs = if (still) FADE_MS else PAGE_MS
            if (still) fadeIn(tween(FADE_MS)) else slideInHorizontally(tween(PAGE_MS)) { -it / 3 }
        }

        fun popExit(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
            val still = reduce || isTabSwitch()
            PageFade.exitMs = if (still) FADE_MS else PAGE_MS / 2
            if (still) ExitTransition.None else slideOutHorizontally(tween(PAGE_MS)) { it }
        }
    }

    object PageFade {
        @Volatile var enterMs: Int = PAGE_MS / 2
        @Volatile var exitMs: Int = PAGE_MS
    }
}

@Composable
fun LazyItemScope.immersiveItemPlacement(): Modifier {
    if (rememberReduceMotion()) return Modifier
    return Modifier.animateItem(
        fadeInSpec = null,
        fadeOutSpec = null,
        placementSpec = ImmersiveMotion.placementSpring(),
    )
}

