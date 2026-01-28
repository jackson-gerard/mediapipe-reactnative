# MediaPipe Fork: Adding Photo Capture

## The Problem

Our scan camera has a ~500-1000ms "blackout" when taking photos. This happens because:

- **MediaPipe** owns the camera for pose detection (skeleton overlay)
- **Expo Camera** owns the camera for `takePictureAsync()`
- They can't coexist—we have to unmount one, mount the other, wait for it to initialize

```
User taps capture → MediaPipe unmounts → Expo Camera mounts → 500ms blackout → Photo taken
```

## The Solution

Fork `@thinksys/react-native-mediapipe` and add a `capturePhoto()` method that grabs the current frame directly from MediaPipe's camera stream. **No switching, no blackout.**

```
User taps capture → MediaPipe captures current frame → Instant → Photo taken
```

## What We Learned

1. **MediaPipe already has frame access** — Every frame passes through `captureOutput()` in `CameraFeedService.swift`. The image data is right there.

2. **They started building this** — There's commented-out code for `uiImageFromPixelBuffer()` and `jpegData()` conversion. They just never exposed it to React Native.

3. **The change is small** — ~50 lines of Swift code across 3 files. No new dependencies.

## Files to Modify (iOS)

| File | Change |
|------|--------|
| `ios/Services/CameraFeedService.swift` | Store latest frame, add `captureCurrentFrame()` method |
| `ios/ViewContoller/CameraView.swift` | Add `capturePhoto()` that saves frame as JPEG |
| `ios/TsMediapipeViewManager.m` | Expose method to React Native |

## Next Steps

1. ✅ Fork repo and get example app building locally
2. ⏳ Implement `capturePhoto()` in iOS native code
3. ⏳ Test in example app
4. ⏳ Update our app's `package.json` to use the fork
5. ⏳ Replace camera switching logic in `ScanCameraScreen.tsx`

## How We'll Use the Fork

```json
// package.json
{
  "dependencies": {
    "@thinksys/react-native-mediapipe": "github:KinoFitness/react-native-mediapipe#main"
  }
}
```

Then `npm install && cd ios && pod install` — works just like the original package.

## Timeline Estimate

- iOS implementation: 4-6 hours
- Testing & integration: 2-3 hours
- Android (later): 4-6 hours

## Resources

- Forked repo: `github.com/KinoFitness/react-native-mediapipe` (TBD)
- Original: `github.com/AkshayMagare/react-native-mediapipe`
