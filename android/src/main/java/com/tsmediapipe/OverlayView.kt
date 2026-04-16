package com.tsmediapipe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

/**
 * OverlayView draws the MediaPipe skeleton lines on top of the camera preview.
 *
 * Styling is matched to the iOS OverlayView (CameraView.swift / OverlayView.swift):
 *   - Line color: white  (iOS: UIColor(red:255 green:255 blue:255 alpha:1))
 *   - Stroke width: 10f  (iOS: lineWidth CGFloat = 4 points — Android dp is denser,
 *                         10f px at typical density produces a visually equivalent line)
 *   - Joint dots: removed (iOS OverlayView does not draw filled point circles
 *                          for body-scan mode; the commented-out block in the
 *                          original Android code is intentionally left out)
 *
 * Body-part gating (face, torso, leftArm, etc.) reads from GlobalState, which is
 * set by TsMediapipeViewManager ReactProps — identical to before.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

  private var results: PoseLandmarkerResult? = null
  private var linePaint = Paint()

  private var scaleFactor: Float = 1f
  private var imageWidth: Int = 1
  private var imageHeight: Int = 1

  init {
    initPaints()
  }

  fun clear() {
    results = null
    linePaint.reset()
    invalidate()
    initPaints()
  }

  private fun initPaints() {
    // ── Match iOS DefaultConstants / OverlayView styling ──────────────────────
    // iOS lineColor = UIColor(red: 255, green: 255, blue: 255, alpha: 1) → white
    linePaint.color = Color.WHITE
    linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
    linePaint.style = Paint.Style.STROKE
    linePaint.strokeCap = Paint.Cap.ROUND   // smoother line ends, matches iOS CoreGraphics default
    linePaint.strokeJoin = Paint.Join.ROUND
    linePaint.isAntiAlias = true
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    val face      = GlobalState.isFaceEnabled
    val torso     = GlobalState.isTorsoEnabled
    val leftArm   = GlobalState.isLeftArmEnabled
    val rightArm  = GlobalState.isRightArmEnabled
    val leftLeg   = GlobalState.isLeftLegEnabled
    val rightLeg  = GlobalState.isRightLegEnabled
    val leftWrist  = GlobalState.isLeftWristEnabled
    val rightWrist = GlobalState.isRightWristEnabled
    val leftAnkle  = GlobalState.isLeftAnkleEnabled
    val rightAnkle = GlobalState.isRightAnkleEnabled

    results?.let { poseLandmarkerResult ->
      for (landmark in poseLandmarkerResult.landmarks()) {

        for (it in PoseLandmarker.POSE_LANDMARKS) {
          val startIdx = it!!.start()
          val endIdx   = it.end()

          // Helper: draw a single connection line
          fun drawConnection() {
            canvas.drawLine(
              poseLandmarkerResult.landmarks()[0][startIdx].x() * imageWidth  * scaleFactor,
              poseLandmarkerResult.landmarks()[0][startIdx].y() * imageHeight * scaleFactor,
              poseLandmarkerResult.landmarks()[0][endIdx].x()   * imageWidth  * scaleFactor,
              poseLandmarkerResult.landmarks()[0][endIdx].y()   * imageHeight * scaleFactor,
              linePaint
            )
          }

          // Face (landmarks 0–10)
          if (face && startIdx in 0..10) drawConnection()

          // Torso: shoulders (11-12), hips (23-24), left side (11-23), right side (12-24)
          if (torso && (
              (startIdx == 11 && endIdx == 12) ||
              (startIdx == 23 && endIdx == 24) ||
              (startIdx == 11 && endIdx == 23) ||
              (startIdx == 12 && endIdx == 24)
            )) drawConnection()

          // Left arm: shoulder→elbow (11-13), elbow→wrist (13-15)
          if (leftArm && (
              (startIdx == 11 && endIdx == 13) ||
              (startIdx == 13 && endIdx == 15)
            )) drawConnection()

          // Right arm: shoulder→elbow (12-14), elbow→wrist (14-16)
          if (rightArm && (
              (startIdx == 12 && endIdx == 14) ||
              (startIdx == 14 && endIdx == 16)
            )) drawConnection()

          // Left leg: hip→knee (23-25), knee→ankle (25-27)
          if (leftLeg && (
              (startIdx == 23 && endIdx == 25) ||
              (startIdx == 25 && endIdx == 27)
            )) drawConnection()

          // Right leg: hip→knee (24-26), knee→ankle (26-28)
          if (rightLeg && (
              (startIdx == 24 && endIdx == 26) ||
              (startIdx == 26 && endIdx == 28)
            )) drawConnection()

          // Left wrist / hand
          if (leftWrist && (
              (startIdx == 15 && endIdx == 21) ||
              (startIdx == 15 && endIdx == 17) ||
              (startIdx == 15 && endIdx == 19) ||
              (startIdx == 17 && endIdx == 19)
            )) drawConnection()

          // Right wrist / hand
          if (rightWrist && (
              (startIdx == 16 && endIdx == 22) ||
              (startIdx == 16 && endIdx == 20) ||
              (startIdx == 16 && endIdx == 18) ||
              (startIdx == 18 && endIdx == 20)
            )) drawConnection()

          // Left ankle / foot
          if (leftAnkle && (
              (startIdx == 27 && endIdx == 29) ||
              (startIdx == 27 && endIdx == 31) ||
              (startIdx == 29 && endIdx == 31)
            )) drawConnection()

          // Right ankle / foot
          if (rightAnkle && (
              (startIdx == 28 && endIdx == 30) ||
              (startIdx == 28 && endIdx == 32) ||
              (startIdx == 30 && endIdx == 32)
            )) drawConnection()
        }
      }
    }
  }

  fun setResults(
    poseLandmarkerResults: PoseLandmarkerResult,
    imageHeight: Int,
    imageWidth: Int,
    runningMode: RunningMode = RunningMode.LIVE_STREAM
  ) {
    results = poseLandmarkerResults
    this.imageHeight = imageHeight
    this.imageWidth  = imageWidth

    scaleFactor = when (runningMode) {
      RunningMode.IMAGE,
      RunningMode.VIDEO -> min(width * 1f / imageWidth, height * 1f / imageHeight)
      RunningMode.LIVE_STREAM ->
        // PreviewView is FILL_START — scale up to match the displayed image size
        max(width * 1f / imageWidth, height * 1f / imageHeight)
    }
    invalidate()
  }

  companion object {
    // 10f matches the visual weight of iOS lineWidth=4pt at standard phone densities
    private const val LANDMARK_STROKE_WIDTH = 10F
  }
}