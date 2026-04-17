package com.tsmediapipe.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.facebook.react.bridge.Promise
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.gson.Gson
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.tsmediapipe.CameraFragmentManager
import com.tsmediapipe.MainViewModel
import com.tsmediapipe.PoseLandmarkerHelper
import com.tsmediapipe.ReactContextProvider
import com.tsmediapipe.databinding.FragmentMyCameraBinding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

  companion object {
    private const val TAG = "Pose Landmarker"
  }

  private var _fragmentCameraBinding: FragmentMyCameraBinding? = null
  private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

  private val fragmentCameraBinding
    get() = _fragmentCameraBinding!!

  private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
  private val viewModel: MainViewModel by activityViewModels()
  private var preview: Preview? = null
  private var imageAnalyzer: ImageAnalysis? = null
  private var camera: Camera? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private var cameraFacing = CameraSelector.LENS_FACING_FRONT

  // ─── Capture: store latest rotated bitmap from the analysis stream ──────────
  // This mirrors iOS's `latestSampleBuffer` in CameraFeedService.swift.
  // We keep the most recent frame so capturePhoto() can grab it instantly
  // without switching camera modes or causing any UX disruption.
  @Volatile private var latestBitmap: Bitmap? = null

  /** Blocking ML operations are performed using this executor */
  private lateinit var backgroundExecutor: ExecutorService

  fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
      if (isGranted) {
        Toast.makeText(context, "Permission request granted", Toast.LENGTH_LONG).show()
        completeCameraSetUpWithPose()
      } else {
        Toast.makeText(context, "Permission request denied", Toast.LENGTH_LONG).show()
      }
    }

  fun completeCameraSetUpWithPose() {
    setUpCamera()

    backgroundExecutor.execute {
      poseLandmarkerHelper = PoseLandmarkerHelper(
        context = requireContext(),
        runningMode = RunningMode.LIVE_STREAM,
        minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
        minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
        minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
        currentDelegate = viewModel.currentDelegate,
        poseLandmarkerHelperListener = this
      )
    }
  }

  override fun onResume() {
    super.onResume()
    backgroundExecutor.execute {
      if (this::poseLandmarkerHelper.isInitialized) {
        if (poseLandmarkerHelper.isClose()) {
          poseLandmarkerHelper.setupPoseLandmarker()
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    if (this::poseLandmarkerHelper.isInitialized) {
      viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
      viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
      viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
      viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)
      backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
    }
  }

  override fun onDestroyView() {
    _fragmentCameraBinding = null
    latestBitmap = null
    super.onDestroyView()
    backgroundExecutor.shutdown()
    backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    CameraFragmentManager.cameraFragment = this
  }

  override fun onDestroy() {
    super.onDestroy()
    CameraFragmentManager.cameraFragment = null
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _fragmentCameraBinding = FragmentMyCameraBinding.inflate(inflater, container, false)
    return fragmentCameraBinding.root
  }

  @SuppressLint("MissingPermission")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    backgroundExecutor = Executors.newSingleThreadExecutor()

    if (!hasPermissions(requireContext())) {
      requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    } else {
      completeCameraSetUpWithPose()
    }
  }

  private fun setUpCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
    cameraProviderFuture.addListener(
      {
        cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases()
      },
      ContextCompat.getMainExecutor(requireContext())
    )
  }

  @SuppressLint("UnsafeOptInUsageError")
  private fun bindCameraUseCases() {
    val cameraProvider = cameraProvider
      ?: throw IllegalStateException("Camera initialization failed.")

    val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

    preview = Preview.Builder()
      .setTargetAspectRatio(AspectRatio.RATIO_4_3)
      .setTargetRotation(_fragmentCameraBinding?.viewFinder?.display?.rotation ?: 0)
      .build()

    imageAnalyzer = ImageAnalysis.Builder()
      .setTargetAspectRatio(AspectRatio.RATIO_4_3)
      .setTargetRotation(_fragmentCameraBinding?.viewFinder?.display?.rotation ?: 0)
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
      .build()
      .also {
        it.setAnalyzer(backgroundExecutor) { image ->
          // Store the latest frame for capturePhoto() before running pose detection.
          // This is the Android equivalent of CameraFeedService.latestSampleBuffer on iOS.
          storeLatestFrame(image)
          detectPose(image)
        }
      }

    cameraProvider.unbindAll()

    try {
      camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
      preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
    } catch (exc: Exception) {
      Log.e(TAG, "Use case binding failed", exc)
    }
  }

  // ─── Frame storage ───────────────────────────────────────────────────────────

  /**
   * Converts the current ImageProxy to a correctly-rotated and mirrored Bitmap,
   * then stores it in [latestBitmap]. Called on every analysis frame so that
   * [capturePhoto] always has a fresh frame available — identical pattern to
   * iOS storing CMSampleBuffer in CameraFeedService.captureCurrentFrame().
   *
   * Note: ImageProxy is closed inside detectPose() via poseLandmarkerHelper,
   * so we must copy pixel data *before* close() is called. We do that here
   * by creating the bitmap first, then letting detectPose() proceed.
   */
  private fun storeLatestFrame(imageProxy: ImageProxy) {
    try {
      val buffer = imageProxy.planes[0].buffer
      // Save buffer position BEFORE reading, so downstream consumers
      // (PoseLandmarkerHelper.detectLiveStream) can read from the same
      // state we received it in. copyPixelsFromBuffer advances the buffer
      // position, and without restoring we crash the pose detector with
      // "Buffer not large enough for pixels".
      val originalPosition = buffer.position()

      val bitmapBuffer = Bitmap.createBitmap(
        imageProxy.width,
        imageProxy.height,
        Bitmap.Config.ARGB_8888
      )
      buffer.rewind()
      bitmapBuffer.copyPixelsFromBuffer(buffer)
      // Restore the buffer's position so detectPose can reuse it safely.
      buffer.position(originalPosition)

      val matrix = Matrix().apply {
        postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        // Mirror front camera horizontally, matching iOS .upMirrored orientation
        if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
          postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
      }

      val rotated = Bitmap.createBitmap(
        bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
      )
      bitmapBuffer.recycle()
      latestBitmap?.recycle()
      latestBitmap = rotated
    } catch (e: Exception) {
      Log.e(TAG, "Error storing latest frame: ${e.message}")
    }
  }

  // ─── Photo capture (mirrors iOS CameraFeedService.captureCurrentFrame) ───────

  /**
   * Grabs the most recently stored frame bitmap, writes it to a temp JPEG file,
   * and resolves the React Native promise with { uri, path }.
   *
   * This matches exactly what iOS does:
   *   - No camera mode switch (no ImageCapture use case needed)
   *   - Uses the live analysis stream's latest frame
   *   - Returns a file:// URI that React Native can consume directly
   *   - JPEG quality 1.0 to match iOS compressionQuality: 1.0
   */
  fun capturePhoto(promise: Promise) {
    Log.d(TAG, "─── CameraFragment.capturePhoto ENTRY ───")
    val bitmap = latestBitmap
    Log.d(TAG, "capturePhoto: latestBitmap = $bitmap")
    if (bitmap == null) {
      Log.e(TAG, "capturePhoto: NO_FRAME — latestBitmap is null. Has the analyzer run yet?")
      promise.reject("NO_FRAME", "No camera frame available yet")
      return
    }
    Log.d(TAG, "capturePhoto: bitmap dimensions = ${bitmap.width}x${bitmap.height}")

    backgroundExecutor.execute {
      try {
        val filename = "mediapipe_capture_${System.currentTimeMillis()}.jpg"
        val file = File(requireContext().cacheDir, filename)
        Log.d(TAG, "capturePhoto: writing to ${file.absolutePath}")
        FileOutputStream(file).use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        Log.d(TAG, "capturePhoto: wrote ${file.length()} bytes")
        val uri = "file://${file.absolutePath}"
        promise.resolve(
          com.facebook.react.bridge.Arguments.createMap().apply {
            putString("uri", uri)
            putString("path", file.absolutePath)
          }
        )
        Log.d(TAG, "capturePhoto: resolved promise with uri=$uri")
      } catch (e: Exception) {
        Log.e(TAG, "capturePhoto failed: ${e.message}", e)
        promise.reject("SAVE_FAILED", e.message)
      }
    }
  }

  // ─── Pose detection ──────────────────────────────────────────────────────────

  private fun detectPose(imageProxy: ImageProxy) {
    if (this::poseLandmarkerHelper.isInitialized) {
      poseLandmarkerHelper.detectLiveStream(
        imageProxy = imageProxy,
        isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
      )
    }
  }

  fun switchCamera() {
    cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
      CameraSelector.LENS_FACING_FRONT
    } else {
      CameraSelector.LENS_FACING_BACK
    }
    Log.d(
      "CameraFragment",
      "Switched camera to ${if (cameraFacing == CameraSelector.LENS_FACING_BACK) "BACK" else "FRONT"}"
    )
    bindCameraUseCases()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    _fragmentCameraBinding?.viewFinder?.display?.let { display ->
      imageAnalyzer?.targetRotation = display.rotation
    }
  }

  // ─── PoseLandmarkerHelper.LandmarkerListener ─────────────────────────────────

  override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
    activity?.runOnUiThread {
      if (_fragmentCameraBinding != null) {

        val data = resultBundle.results.first()
        val landmarksArray: MutableList<Map<String, Any>> = mutableListOf()
        val worldLandmarksArray: MutableList<Map<String, Any>> = mutableListOf()

        val landmarks = data.landmarks()
        val worldLandmarks = data.worldLandmarks()

        if (landmarks.isNotEmpty()) {
          for (landmark in landmarks[0]) {
            landmarksArray.add(
              mapOf(
                "x" to landmark.x(),
                "y" to landmark.y(),
                "z" to landmark.z(),
                "visibility" to landmark.visibility().get(),
                "presence" to landmark.presence().get()
              )
            )
          }
        }

        worldLandmarks?.let {
          if (it.isNotEmpty() && it[0].size == 33) {
            for (worldLandmark in it[0]) {
              worldLandmarksArray.add(
                mapOf(
                  "x" to worldLandmark.x(),
                  "y" to worldLandmark.y(),
                  "z" to worldLandmark.z(),
                  "visibility" to worldLandmark.visibility().get(),
                  "presence" to worldLandmark.presence().get()
                )
              )
            }
          }
        }

        val swiftDict: MutableMap<String, Any> = mutableMapOf(
          "landmarks" to landmarksArray,
          "additionalData" to mapOf(
            "height" to resultBundle.inputImageHeight,
            "width" to resultBundle.inputImageWidth
          ),
          "worldLandmarks" to worldLandmarksArray
        )

        val gson = Gson()
        val jsonData = gson.toJson(swiftDict)

        // Emit via DeviceEventEmitter — consumed by the useFrameAndTiltDetection
        // listener we added on the JS side with DeviceEventEmitter.addListener('onLandmark', ...)
        val reactContext = ReactContextProvider.reactApplicationContext
        reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          ?.emit("onLandmark", jsonData)

        fragmentCameraBinding.myOverlay.setResults(
          resultBundle.results.first(),
          resultBundle.inputImageHeight,
          resultBundle.inputImageWidth,
          RunningMode.LIVE_STREAM
        )
        fragmentCameraBinding.myOverlay.invalidate()
      }
    }
  }

  override fun onError(error: String, errorCode: Int) {
    activity?.runOnUiThread {
      Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
      Log.e(TAG, "PoseLandmarker error [$errorCode]: $error")
    }
  }
}