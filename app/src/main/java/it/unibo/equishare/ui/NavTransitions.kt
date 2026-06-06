/** Defines shared navigation transition animations. */
package it.unibo.equishare.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val EmphasizedStandard   = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val FORWARD_ENTER_MS = 380
private const val FORWARD_EXIT_MS  = 320
private const val POP_ENTER_MS     = 360
private const val POP_EXIT_MS      = 280
private const val MODAL_ENTER_MS   = 420
private const val MODAL_EXIT_MS    = 280
private const val PARALLAX_FRACTION = 0.30f
// Tab switches cross-fade in place so the bottom bar stays still.
private const val TAB_FADE_MS = 180

private val ModalScaleOrigin = TransformOrigin(0.5f, 0.5f)

// ── Tab-aware dispatcher ─────────────────────────────────────────────────────

private fun NavBackStackEntry.isTabRoute(): Boolean =
    destination.hasRoute(EquiShareRoute.Groups::class) ||
    destination.hasRoute(EquiShareRoute.Activity::class) ||
    destination.hasRoute(EquiShareRoute.Statistics::class)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.isTabRoute() && targetState.isTabRoute()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveEnterTransition(): EnterTransition =
    if (isTabSwitch()) tabFadeIn() else defaultEnterTransition()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.resolveExitTransition(): ExitTransition =
    if (isTabSwitch()) tabFadeOut() else defaultExitTransition()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopEnterTransition(): EnterTransition =
    if (isTabSwitch()) tabFadeIn() else defaultPopEnterTransition()

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.resolvePopExitTransition(): ExitTransition =
    if (isTabSwitch()) tabFadeOut() else defaultPopExitTransition()

private fun tabFadeIn(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = TAB_FADE_MS, easing = EmphasizedStandard))

private fun tabFadeOut(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = TAB_FADE_MS, easing = EmphasizedStandard))

// ── Default (sibling) transitions ────────────────────────────────────────────

private fun AnimatedContentTransitionScope<*>.defaultEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = SlideDirection.Start,
        animationSpec = tween(durationMillis = FORWARD_ENTER_MS, easing = EmphasizedDecelerate),
    ) + fadeIn(
        animationSpec = tween(durationMillis = 220, easing = EmphasizedStandard),
    )

private fun AnimatedContentTransitionScope<*>.defaultExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = SlideDirection.Start,
        animationSpec = tween(durationMillis = FORWARD_EXIT_MS, easing = EmphasizedAccelerate),
        targetOffset = { fullWidth -> -(fullWidth * PARALLAX_FRACTION).toInt() },
    ) + fadeOut(
        animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate),
    )

private fun AnimatedContentTransitionScope<*>.defaultPopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = SlideDirection.End,
        animationSpec = tween(durationMillis = POP_ENTER_MS, easing = EmphasizedDecelerate),
        initialOffset = { fullWidth -> -(fullWidth * PARALLAX_FRACTION).toInt() },
    ) + fadeIn(
        animationSpec = tween(durationMillis = 200, easing = EmphasizedStandard),
    )

private fun AnimatedContentTransitionScope<*>.defaultPopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = SlideDirection.End,
        animationSpec = tween(durationMillis = POP_EXIT_MS, easing = EmphasizedAccelerate),
    ) + fadeOut(
        animationSpec = tween(durationMillis = 180, easing = EmphasizedAccelerate),
    )

// ── Modal (bottom-sheet style) transitions ───────────────────────────────────

internal fun AnimatedContentTransitionScope<*>.modalEnterTransition(): EnterTransition =
    slideInVertically(
        animationSpec = tween(durationMillis = MODAL_ENTER_MS, easing = EmphasizedDecelerate),
        initialOffsetY = { fullHeight -> fullHeight },
    ) + fadeIn(
        animationSpec = tween(durationMillis = 200, easing = EmphasizedStandard),
    )

internal fun AnimatedContentTransitionScope<*>.modalExitTransition(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(durationMillis = MODAL_EXIT_MS, easing = EmphasizedAccelerate),
        targetOffsetY = { fullHeight -> fullHeight },
    ) + fadeOut(
        animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerate),
    )

internal fun AnimatedContentTransitionScope<*>.modalHoldExitTransition(): ExitTransition =
    scaleOut(
        targetScale = 0.96f,
        transformOrigin = ModalScaleOrigin,
        animationSpec = tween(durationMillis = MODAL_ENTER_MS, easing = EmphasizedStandard),
    ) + fadeOut(
        targetAlpha = 0.6f,
        animationSpec = tween(durationMillis = MODAL_ENTER_MS, easing = EmphasizedStandard),
    )

internal fun AnimatedContentTransitionScope<*>.modalHoldEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = 0.96f,
        transformOrigin = ModalScaleOrigin,
        animationSpec = tween(durationMillis = MODAL_EXIT_MS, easing = EmphasizedDecelerate),
    ) + fadeIn(
        initialAlpha = 0.6f,
        animationSpec = tween(durationMillis = MODAL_EXIT_MS, easing = EmphasizedDecelerate),
    )
