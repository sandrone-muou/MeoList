package com.meolist.feature.farm

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.meolist.domain.farm.Crop
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn crops: six looks per crop (seed to ripe). The art is deliberately simple vector shapes
 * so it scales crisply and needs no bitmap assets.
 */
@Composable
fun CropArt(crop: Crop, stage: Int, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = description }) {
        drawCrop(crop, stage)
    }
}

private val Leaf = Color(0xFF6A9F4B)
private val LeafDark = Color(0xFF4C7A35)
private val Soil = Color(0xFF8D6E63)
private val SoilDark = Color(0xFF6D4C41)
private val Carrot = Color(0xFFF4511E)
private val Tomato = Color(0xFFE53935)
private val Sunflower = Color(0xFFF9A825)
private val SunflowerHeart = Color(0xFF6D4C41)
private val Pumpkin = Color(0xFFFB8C00)
private val Grape = Color(0xFF7E57C2)
private val Cherry = Color(0xFFC2185B)
private val Seed = Color(0xFF7B5E45)

private fun DrawScope.drawCrop(crop: Crop, requestedStage: Int) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val stage = requestedStage.coerceIn(0, 5)
    val groundY = h * 0.80f
    val growth = stage / 5f
    val fruitScale = when (stage) {
        0, 1, 2 -> 0f
        3 -> 0.65f
        4 -> 0.85f
        else -> 1f
    }

    // Soil mound with a darker rim.
    drawOval(SoilDark.copy(alpha = 0.35f), Offset(w * 0.10f, groundY - h * 0.04f), Size(w * 0.80f, h * 0.18f))
    drawOval(Soil, Offset(w * 0.14f, groundY - h * 0.05f), Size(w * 0.72f, h * 0.15f))

    if (stage == 0) {
        drawOval(Seed, Offset(w * 0.42f, groundY - h * 0.045f), Size(w * 0.16f, h * 0.09f))
        drawLine(LeafDark, Offset(w * 0.50f, groundY - h * 0.035f), Offset(w * 0.50f, groundY - h * 0.11f), w * 0.025f, StrokeCap.Round)
        leaf(Offset(w * 0.50f, groundY - h * 0.10f), w * 0.09f, -40f)
        leaf(Offset(w * 0.50f, groundY - h * 0.10f), w * 0.08f, 220f)
        return
    }

    val stemTop = groundY - h * (0.16f + 0.30f * growth)
    drawLine(LeafDark, Offset(w / 2, groundY), Offset(w / 2, stemTop), w * 0.035f, StrokeCap.Round)
    fun onStem(fraction: Float) = groundY - (groundY - stemTop) * fraction
    if (stage >= 1) {
        leaf(Offset(w * 0.5f, onStem(0.55f)), w * 0.11f, 200f)
        leaf(Offset(w * 0.5f, onStem(0.42f)), w * 0.11f, -20f)
    }
    if (stage >= 2) {
        leaf(Offset(w * 0.5f, onStem(0.75f)), w * 0.16f, -35f)
        leaf(Offset(w * 0.5f, onStem(0.30f)), w * 0.14f, 215f)
    }
    if (stage >= 3) {
        leaf(Offset(w * 0.5f, onStem(0.88f)), w * 0.13f, 35f)
    }

    when (crop) {
        Crop.CARROT -> drawCarrot(stage, fruitScale, groundY, w, h)
        Crop.TOMATO -> drawTomato(stage, fruitScale, stemTop, w, h)
        Crop.SUNFLOWER -> drawSunflower(stage, fruitScale, stemTop, w)
        Crop.PUMPKIN -> drawPumpkin(stage, fruitScale, groundY, w, h)
        Crop.GRAPE -> drawGrape(stage, fruitScale, stemTop, w, h)
        Crop.CHERRY -> drawCherry(stage, fruitScale, stemTop, w, h)
    }

    if (stage == 5) {
        // Little sparkles mark a ripe crop.
        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.022f, Offset(w * 0.76f, groundY - h * 0.62f))
        drawCircle(Color(0xFFFFF59D), w * 0.014f, Offset(w * 0.24f, groundY - h * 0.46f))
    }
}

private fun DrawScope.leaf(at: Offset, length: Float, angle: Float) {
    rotate(angle, pivot = at) {
        drawOval(Leaf, Offset(at.x, at.y - length * 0.22f), Size(length, length * 0.44f))
    }
}

private fun DrawScope.drawCarrot(stage: Int, fruitScale: Float, groundY: Float, w: Float, h: Float) {
    if (stage < 3) return
    val rootHeight = h * 0.20f * fruitScale
    val halfWidth = w * 0.09f * fruitScale + w * 0.015f
    val path = Path().apply {
        moveTo(w / 2 - halfWidth, groundY - rootHeight * 0.55f)
        quadraticTo(w / 2 - halfWidth * 0.9f, groundY + rootHeight * 0.45f, w / 2, groundY + rootHeight * 0.75f)
        quadraticTo(w / 2 + halfWidth * 0.9f, groundY + rootHeight * 0.45f, w / 2 + halfWidth, groundY - rootHeight * 0.55f)
        close()
    }
    drawPath(path, Carrot)
    // Ribs.
    for (i in -1..1) {
        val x = w / 2 + i * halfWidth * 0.4f
        drawLine(Carrot.copy(alpha = 0.45f), Offset(x, groundY - rootHeight * 0.2f), Offset(x + i * w * 0.015f, groundY + rootHeight * 0.45f), w * 0.012f)
    }
    // Leafy top.
    leafTop(Offset(w / 2, groundY - rootHeight * 0.55f), w * 0.16f)
}

private fun DrawScope.leafTop(at: Offset, length: Float) {
    rotate(-28f, pivot = at) { drawOval(LeafDark, Offset(at.x - length, at.y - length * 0.8f), Size(length * 1.3f, length * 0.3f)) }
    rotate(-90f, pivot = at) { drawOval(Leaf, Offset(at.x - length, at.y - length * 0.6f), Size(length * 1.3f, length * 0.3f)) }
    rotate(28f, pivot = at) { drawOval(LeafDark, Offset(at.x + length * 0.05f, at.y - length * 0.75f), Size(length * 1.3f, length * 0.3f)) }
}

private fun DrawScope.drawTomato(stage: Int, fruitScale: Float, top: Float, w: Float, h: Float) {
    if (stage < 3) return
    val radius = w * 0.085f * fruitScale + w * 0.01f
    val centers = listOf(
        Offset(w * 0.36f, top + h * 0.20f),
        Offset(w * 0.64f, top + h * 0.16f),
        Offset(w * 0.50f, top + h * 0.30f),
    )
    centers.forEachIndexed { index, center ->
        if (stage == 3 && index == 2) return@forEachIndexed
        drawCircle(Tomato, radius, center)
        drawCircle(Tomato.copy(alpha = 0.25f), radius * 0.55f, Offset(center.x - radius * 0.25f, center.y - radius * 0.25f))
        // Sepals.
        drawLine(LeafDark, Offset(center.x, center.y - radius), Offset(center.x - radius * 0.35f, center.y - radius * 0.85f), radius * 0.16f, StrokeCap.Round)
        drawLine(LeafDark, Offset(center.x, center.y - radius), Offset(center.x + radius * 0.35f, center.y - radius * 0.85f), radius * 0.16f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSunflower(stage: Int, fruitScale: Float, top: Float, w: Float) {
    if (stage < 2) return
    val center = Offset(w / 2, top)
    val heart = w * (0.07f + 0.035f * fruitScale)
    val petal = heart * 0.75f
    val count = 12
    for (i in 0 until count) {
        val angle = Math.toRadians((i * 360.0 / count))
        val at = Offset(center.x + cos(angle).toFloat() * heart * 1.35f, center.y + sin(angle).toFloat() * heart * 1.35f)
        drawCircle(Sunflower, petal, at)
    }
    drawCircle(SunflowerHeart, heart, center)
    drawCircle(SunflowerHeart.copy(alpha = 0.5f), heart * 0.55f, Offset(center.x - heart * 0.2f, center.y - heart * 0.2f))
}

private fun DrawScope.drawPumpkin(stage: Int, fruitScale: Float, groundY: Float, w: Float, h: Float) {
    if (stage < 3) return
    val width = w * 0.34f * fruitScale + w * 0.08f
    val height = h * 0.20f * fruitScale + h * 0.05f
    val topLeft = Offset(w / 2 - width / 2, groundY - height * 0.55f)
    drawOval(Pumpkin, topLeft, Size(width, height))
    drawOval(LeafDark, Offset(w / 2 - width * 0.06f, topLeft.y - height * 0.15f), Size(width * 0.12f, height * 0.25f))
    for (i in -1..1) {
        val x = w / 2 + i * width * 0.22f
        drawLine(Pumpkin.copy(alpha = 0.5f), Offset(x, topLeft.y + height * 0.15f), Offset(x, topLeft.y + height * 0.85f), w * 0.012f)
    }
}

private fun DrawScope.drawGrape(stage: Int, fruitScale: Float, top: Float, w: Float, h: Float) {
    if (stage < 3) return
    val r = w * 0.048f * fruitScale + w * 0.006f
    val cx = w * 0.58f
    val cy = top + h * 0.18f
    val offsets = listOf(
        0f to 0f,
        -1.1f to 0.8f, 1.1f to 0.8f,
        -0.55f to 1.6f, 0.55f to 1.6f,
        0f to 2.4f,
    )
    offsets.forEach { (dx, dy) ->
        drawCircle(Grape, r, Offset(cx + dx * r, cy + dy * r))
        drawCircle(Color.White.copy(alpha = 0.18f), r * 0.4f, Offset(cx + dx * r - r * 0.25f, cy + dy * r - r * 0.25f))
    }
    drawLine(LeafDark, Offset(cx, cy - r), Offset(cx - w * 0.03f, top + h * 0.04f), w * 0.018f, StrokeCap.Round)
}

private fun DrawScope.drawCherry(stage: Int, fruitScale: Float, top: Float, w: Float, h: Float) {
    if (stage < 3) return
    val r = w * 0.075f * fruitScale + w * 0.012f
    val left = Offset(w * 0.40f, top + h * 0.28f)
    val right = Offset(w * 0.62f, top + h * 0.32f)
    drawLine(LeafDark, Offset(w * 0.50f, top + h * 0.05f), Offset(left.x, left.y - r * 0.6f), w * 0.018f, StrokeCap.Round)
    drawLine(LeafDark, Offset(w * 0.50f, top + h * 0.05f), Offset(right.x, right.y - r * 0.6f), w * 0.018f, StrokeCap.Round)
    listOf(left, right).forEach { center ->
        drawCircle(Cherry, r, center)
        drawCircle(Color.White.copy(alpha = 0.22f), r * 0.35f, Offset(center.x - r * 0.3f, center.y - r * 0.3f))
    }
}
