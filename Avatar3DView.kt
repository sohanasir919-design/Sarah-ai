package com.sarah.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNode

private const val MODEL_PATH = "models/sarah.glb"

/**
 * 3D Sarah avatar. Loops idle animation 0; while [speaking], drives morph target 0 (mouth open)
 * with [amplitude] (0..1) for lip-sync. Falls back to a pulsing orb if sarah.glb is missing.
 */
@Composable
fun Avatar3DView(speaking: Boolean, amplitude: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hasModel = remember { try { ctx.assets.open(MODEL_PATH).close(); true } catch (_: Exception) { false } }
    if (!hasModel) { PlaceholderOrb(speaking, amplitude, modifier); return }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) { position = Position(y = 0.35f, z = 1.6f) }
    val centerNode = rememberNode(engine)

    val modelNode = remember(modelLoader) {
        try {
            val inst = modelLoader.createModelInstance(MODEL_PATH)
            ModelNode(modelInstance = inst, scaleToUnits = 1.0f, centerOrigin = Position(y = -0.5f)).apply {
                if (inst.animator.animationCount > 0) playAnimation(0, loop = true)   // idle loop
            }
        } catch (e: Exception) { null }
    }

    // Lip-sync: drive the first morph target (mouth) from audio amplitude.
    LaunchedEffect(amplitude, speaking) {
        val node = modelNode ?: return@LaunchedEffect
        val w = if (speaking) amplitude.coerceIn(0f, 1f) else 0f
        try {
            val rm = engine.renderableManager
            node.renderableNodes.forEach { rn ->
                val inst = rm.getInstance(rn.entity)
                if (inst != 0) rm.setMorphWeights(inst, floatArrayOf(w), 0)
            }
        } catch (_: Exception) { /* model has no morph targets */ }
    }

    Scene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator(),
        childNodes = listOfNotNull(centerNode, modelNode),
        isOpaque = false
    )
}

@Composable
private fun PlaceholderOrb(speaking: Boolean, amplitude: Float, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val s = if (speaking) 1f + amplitude * 0.35f else 1f
        Box(
            Modifier.size(140.dp).scale(s)
                .background(if (speaking) Color(0xFFFF4F9A) else Color(0xFF9B5DE5), CircleShape)
        )
    }
}
