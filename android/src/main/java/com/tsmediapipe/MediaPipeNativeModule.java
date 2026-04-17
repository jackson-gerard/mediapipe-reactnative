package com.tsmediapipe;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class MediaPipeNativeModule extends ReactContextBaseJavaModule {

  private static final String TAG = "MediaPipeNativeModule";

  public MediaPipeNativeModule(ReactApplicationContext reactContext) {
    super(reactContext);
    ReactContextProvider.reactApplicationContext = reactContext;
  }

  @Override
  public String getName() {
    return "MediaPipeNativeModule";
  }

  /**
   * Switches between front and back camera.
   * Unchanged from original.
   */
  @ReactMethod
  public void switchCameraMethod() {
    Log.d(TAG, "switchCameraMethod called");
    AppCompatActivity activity = (AppCompatActivity) getCurrentActivity();

    if (activity != null && CameraFragmentManager.INSTANCE.getCameraFragment() != null) {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          CameraFragmentManager.INSTANCE.getCameraFragment().switchCamera();
        }
      });
    } else {
      Log.e(TAG, "switchCameraMethod: CameraFragment is not initialized");
    }
  }

  /**
   * Captures the current camera frame and returns a file URI.
   *
   * This mirrors iOS MediaPipeModule.swift capturePhoto(_:rejecter:), which calls
   * CameraFeedService.captureCurrentFrame() to grab the latest sample buffer
   * without switching camera modes.
   *
   * On Android we store each analysis frame as a Bitmap in CameraFragment
   * (latestBitmap), then write it to a JPEG in the cache directory here.
   *
   * Resolves with: { uri: "file:///...", path: "/..." }
   * Rejects with:  "NO_FRAGMENT" | "NO_FRAME" | "SAVE_FAILED"
   */
  @ReactMethod
  public void capturePhoto(Promise promise) {
    Log.d(TAG, "─── capturePhoto ENTRY ───");

    AppCompatActivity activity = (AppCompatActivity) getCurrentActivity();
    if (activity == null) {
      Log.e(TAG, "capturePhoto: getCurrentActivity() returned null");
      promise.reject("NO_FRAGMENT", "Activity is null");
      return;
    }
    Log.d(TAG, "capturePhoto: activity = " + activity.getClass().getSimpleName());

    com.tsmediapipe.fragment.CameraFragment fragment =
        CameraFragmentManager.INSTANCE.getCameraFragment();

    Log.d(TAG, "capturePhoto: CameraFragmentManager.cameraFragment = " + fragment);

    if (fragment == null) {
      Log.e(TAG, "capturePhoto: CameraFragment not set in CameraFragmentManager. "
          + "Was CameraFragment.onCreate() called? Is the TsMediapipeView mounted?");
      promise.reject("NO_FRAGMENT", "CameraFragment not initialized");
      return;
    }

    Log.d(TAG, "capturePhoto: delegating to fragment.capturePhoto()");
    fragment.capturePhoto(promise);
  }
}