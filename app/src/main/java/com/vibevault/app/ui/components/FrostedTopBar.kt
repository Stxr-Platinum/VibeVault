package com.vibevault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibevault.app.R
import com.vibevault.app.constants.ReduceDynamicBlurKey
import com.vibevault.app.utils.rememberPreference

val TopBarContentHeight = 52.dp
val TopBarContentGap = 12.dp

@Composable
fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarContentHeight

@Composable
fun topBarContentPadding(): Dp = topBarHeight() + TopBarContentGap

/**
 * The top bar's content — title, back affordance, actions — over no backdrop of its own.
 * The glass behind it is TopFadeBlur's, drawn underneath.
 * Under "Reduce dynamic blur", it fills itself solid with MaterialTheme.colorScheme.surface.
 */
@Composable
fun FrostedTopBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    refreshing: Boolean = false,
    pullFraction: () -> Float = { 0f },
    actions: @Composable () -> Unit = {},
    unscrolledStartContent: (@Composable () -> Unit)? = null,
    fadeActionsOnScroll: Boolean = false,
) {
    val (reduceDynamicBlur) = rememberPreference(ReduceDynamicBlurKey, defaultValue = false)
    val titleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(220),
        label = "topBarTitleAlpha",
    )
    val unscrolledAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0f else 1f,
        animationSpec = tween(220),
        label = "topBarUnscrolledAlpha",
    )
    val dividerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outline.copy(
            alpha = if (scrolled && reduceDynamicBlur) 0.6f else 0f,
        ),
        animationSpec = tween(220),
        label = "topBarDivider",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (reduceDynamicBlur) Modifier.background(MaterialTheme.colorScheme.surface)
                else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarContentHeight),
        ) {
            if (unscrolledStartContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .graphicsLayer { alpha = unscrolledAlpha },
                ) {
                    if (unscrolledAlpha > 0.05f) {
                        unscrolledStartContent()
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 96.dp)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = titleAlpha },
            )

            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .graphicsLayer {
                        if (fadeActionsOnScroll) {
                            alpha = unscrolledAlpha
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!fadeActionsOnScroll || unscrolledAlpha > 0.05f) {
                    actions()
                }
            }
        }

        Box(Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
            RefreshLine(
                refreshing = refreshing,
                pullFraction = pullFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun RefreshLine(refreshing: Boolean, pullFraction: () -> Float, modifier: Modifier = Modifier) {
    val fraction = pullFraction()
    val pulling = fraction > 0.01f
    AnimatedVisibility(
        visible = refreshing || pulling,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        val lineModifier = Modifier
            .fillMaxWidth()
            .height(2.5.dp)
        if (refreshing) {
            LinearProgressIndicator(
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
