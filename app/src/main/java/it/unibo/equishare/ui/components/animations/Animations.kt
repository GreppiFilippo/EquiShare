/** Defines shared UI animation helpers. */
package it.unibo.equishare.ui.components.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object EquiMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val EmphasizedStandard   = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // Spring tuned for chunky UI elements (FABs, cards) — visible bounce.
    val ChunkySpring = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // Spring tuned for crisp, no-overshoot motion (segmented controls, chips).
    val CrispSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMedium,
    )
}

@Composable
fun Modifier.animateListItemEntry(
    index: Int,
    itemDelayMs: Int = 40,
    baseDelayMs: Int = 60,
    translationDp: Dp = 24.dp,
    maxStaggeredItems: Int = 12,
): Modifier {
    val density = LocalDensity.current
    val translationPx = with(density) { translationDp.toPx() }
    val effectiveIndex = index.coerceAtMost(maxStaggeredItems)

    val progress = remember(index) { Animatable(0f) }

    LaunchedEffect(index) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 380,
                delayMillis = baseDelayMs + effectiveIndex * itemDelayMs,
                easing = EquiMotion.EmphasizedDecelerate,
            ),
        )
    }

    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * translationPx
    }
}

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
