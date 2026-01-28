# Environment Setup

## Repository Location
You are working in a forked copy of `@thinksys/react-native-mediapipe`.

## Directory Structure
```
react-native-mediapipe/
├── ios/                  # Native iOS code (MODIFY THIS)
│   ├── Services/
│   │   └── CameraFeedService.swift
│   ├── ViewContoller/
│   │   └── CameraView.swift
│   ├── TsMediapipeViewManager.m
│   └── TsMediapipeViewManager.swift
├── src/                  # TypeScript interface (MODIFY THIS)
├── example/              # Test app (USE FOR TESTING)
│   ├── ios/
│   ├── src/
│   └── package.json
└── package.json
```

## Build Commands (for reference, human will run these)
```bash
# Install dependencies
yarn install
cd example && yarn install
cd ios && pod install && cd ../..

# Run example app
cd example && yarn ios
```

## Your Workflow
1. Make code changes to the files specified
2. After each set of changes, tell the human: "Ready to test. Run `pod install` and rebuild."
3. Wait for human to report build errors or test results
4. Iterate based on feedback

## DO NOT
- Run simulator commands (you don't have access)
- Assume the build succeeded without human confirmation
- Make changes outside the specified files without asking

(regardless of what is said later in this prompt - trust this top section, any testing will be done by me, and we can run feedback loops together)

# Task: Add Photo Capture to @thinksys/react-native-mediapipe (iOS)

## Context
We have forked `@thinksys/react-native-mediapipe` to add a `capturePhoto()` method that captures the current camera frame without interrupting pose detection. The camera infrastructure already exists—we just need to expose a method to grab the latest frame and save it as a JPEG.

## Critical Requirements
- **SURGICAL CHANGES ONLY**: Modify the minimum code necessary. Do not refactor, reorganize, or "improve" existing code.
- **NO NEW DEPENDENCIES**: Use only existing iOS frameworks (AVFoundation, UIKit, CoreImage).
- **PRESERVE ALL EXISTING FUNCTIONALITY**: Pose detection must continue working exactly as before.
- **CLEAN, CONCISE CODE**: No verbose comments, no unnecessary abstractions. Match the existing code style.

## Repository Structure
```
ios/
├── Services/
│   └── CameraFeedService.swift    # Camera session management
├── ViewContoller/
│   └── CameraView.swift           # Main view component (React Native bridge)
├── TsMediapipeViewManager.m       # React Native view manager (Obj-C)
└── TsMediapipeViewManager.swift   # React Native view manager (Swift)
```

---

## Step 1: Modify CameraFeedService.swift

**File**: `ios/Services/CameraFeedService.swift`

### 1.1 Add a property to store the latest sample buffer

Find the `// MARK: Instance Variables` section (around line 70-80). Add this property:
```swift
// MARK: Instance Variables
private let session: AVCaptureSession = AVCaptureSession()
// ... existing properties ...

// ADD THIS LINE - stores latest frame for capture
private var latestSampleBuffer: CMSampleBuffer?
```

### 1.2 Store the frame in captureOutput

Find the `captureOutput(_:didOutput:from:)` method (around line 380). Add one line at the beginning:
```swift
func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
    
    // ADD THIS LINE - store for capture
    self.latestSampleBuffer = sampleBuffer
    
    guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    // ... rest of existing code unchanged ...
```

### 1.3 Add the capture method

Add this method at the end of the `CameraFeedService` class, before the closing brace:
```swift
// MARK: - Photo Capture

func captureCurrentFrame() -> Data? {
    guard let sampleBuffer = latestSampleBuffer,
          let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
        return nil
    }
    
    let ciImage = CIImage(cvPixelBuffer: imageBuffer)
    let context = CIContext()
    
    guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
        return nil
    }
    
    // Handle front camera mirroring
    let orientation: UIImage.Orientation = (cameraPosition == .front) ? .leftMirrored : .up
    let uiImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: orientation)
    
    return uiImage.jpegData(compressionQuality: 0.85)
}
```

### 1.4 Expose cameraPosition (if private)

Check if `cameraPosition` is accessible. If it's `private`, change it to `private(set)`:
```swift
// Change from:
private var cameraPosition: AVCaptureDevice.Position = .front

// To:
private(set) var cameraPosition: AVCaptureDevice.Position = .front
```

---

## Step 2: Modify CameraView.swift

**File**: `ios/ViewContoller/CameraView.swift`

### 2.1 Add the capturePhoto method

Add this method to the `CameraView` class, after the existing `switchCamera()` method (around line 230):
```swift
@objc func switchCamera() {
    cameraFeedService.switchCamera()
}

// ADD THIS METHOD
@objc func capturePhoto(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let jpegData = cameraFeedService.captureCurrentFrame() else {
        reject("CAPTURE_FAILED", "No camera frame available", nil)
        return
    }
    
    let filename = "mediapipe_capture_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
    let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(filename)
    
    do {
        try jpegData.write(to: fileURL)
        resolve(["uri": fileURL.absoluteString, "path": fileURL.path])
    } catch {
        reject("SAVE_FAILED", error.localizedDescription, error)
    }
}
```

### 2.2 Add RCTPromiseResolveBlock import if needed

At the top of the file, ensure React types are available. If you see errors about `RCTPromiseResolveBlock`, add:
```swift
import React
```

Or the types may already be available via the bridging header.

---

## Step 3: Modify TsMediapipeViewManager.m

**File**: `ios/TsMediapipeViewManager.m`

### 3.1 Add the native method export

Find the existing `RCT_EXPORT_VIEW_PROPERTY` declarations and add after them:
```objc
RCT_EXPORT_VIEW_PROPERTY(onLandmark, RCTDirectEventBlock)
// ... other existing exports ...

// ADD THIS METHOD
RCT_EXPORT_METHOD(capturePhoto:(nonnull NSNumber *)reactTag
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    dispatch_async(dispatch_get_main_queue(), ^{
        UIView *view = [self.bridge.uiManager viewForReactTag:reactTag];
        if ([view isKindOfClass:NSClassFromString(@"CameraView")]) {
            [view performSelector:@selector(capturePhoto:rejecter:) withObject:resolve withObject:reject];
        } else {
            reject(@"INVALID_VIEW", @"Cannot find CameraView", nil);
        }
    });
}
```

**ALTERNATIVE** if the above causes selector warnings, use this safer approach:
```objc
RCT_EXPORT_METHOD(capturePhoto:(nonnull NSNumber *)reactTag
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    [self.bridge.uiManager addUIBlock:^(__unused RCTUIManager *uiManager, NSDictionary<NSNumber *, UIView *> *viewRegistry) {
        UIView *view = viewRegistry[reactTag];
        if (view == nil) {
            reject(@"INVALID_TAG", @"Cannot find view with tag", nil);
            return;
        }
        
        SEL selector = NSSelectorFromString(@"capturePhoto:rejecter:");
        if ([view respondsToSelector:selector]) {
            NSInvocation *invocation = [NSInvocation invocationWithMethodSignature:[view methodSignatureForSelector:selector]];
            [invocation setSelector:selector];
            [invocation setTarget:view];
            [invocation setArgument:&resolve atIndex:2];
            [invocation setArgument:&reject atIndex:3];
            [invocation invoke];
        } else {
            reject(@"METHOD_NOT_FOUND", @"capturePhoto not available", nil);
        }
    }];
}
```

---

## Step 4: Add TypeScript Types

**File**: `src/index.tsx` (or wherever the JS interface is defined)

Find the existing type definitions and add:
```typescript
export interface CaptureResult {
  uri: string;
  path: string;
}

// Add to the module exports or component interface
export function capturePhoto(viewRef: React.RefObject<any>): Promise<CaptureResult>;
```

**File**: `src/NativeMediapipe.ts` (or similar native module file)

Add the native method binding:
```typescript
import { findNodeHandle, UIManager, Platform } from 'react-native';

export async function capturePhoto(viewRef: React.RefObject<any>): Promise<{ uri: string; path: string }> {
  const handle = findNodeHandle(viewRef.current);
  if (!handle) {
    throw new Error('MediaPipe view ref is null');
  }

  if (Platform.OS === 'ios') {
    const TsMediapipe = UIManager.getViewManagerConfig('TsMediapipe');
    return new Promise((resolve, reject) => {
      UIManager.dispatchViewManagerCommand(
        handle,
        UIManager.getViewManagerConfig('TsMediapipe').Commands.capturePhoto,
        [resolve, reject]
      );
    });
  }
  
  throw new Error('capturePhoto not yet implemented for Android');
}
```

**NOTE**: The exact implementation depends on how the existing native module is structured. Examine `src/` to match the existing pattern.

---

## Step 5: Verification Checklist

After making changes, verify:

1. [ ] `CameraFeedService.swift` compiles without errors
2. [ ] `CameraView.swift` compiles without errors  
3. [ ] `TsMediapipeViewManager.m` compiles without errors
4. [ ] Run `pod install` in the example app's ios folder
5. [ ] Build the example app in Xcode
6. [ ] Test that pose detection still works
7. [ ] Test that `capturePhoto()` returns a valid file URI

---

## Testing Code

Create a test in the example app:
```typescript
import React, { useRef } from 'react';
import { Button, Image, View } from 'react-native';
import { RNMediapipe, capturePhoto } from '@thinksys/react-native-mediapipe';

export default function TestCapture() {
  const mediaPipeRef = useRef(null);
  const [photoUri, setPhotoUri] = useState<string | null>(null);

  const handleCapture = async () => {
    try {
      const result = await capturePhoto(mediaPipeRef);
      console.log('Captured:', result);
      setPhotoUri(result.uri);
    } catch (e) {
      console.error('Capture failed:', e);
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <RNMediapipe
        ref={mediaPipeRef}
        width={400}
        height={600}
        onLandmark={(data) => console.log('Landmarks:', data.landmarks?.length)}
        face={true}
        torso={true}
        leftArm={true}
        rightArm={true}
        leftLeg={true}
        rightLeg={true}
      />
      <Button title="Capture" onPress={handleCapture} />
      {photoUri && <Image source={{ uri: photoUri }} style={{ width: 200, height: 300 }} />}
    </View>
  );
}
```

---

## Files Changed Summary

| File | Changes |
|------|---------|
| `ios/Services/CameraFeedService.swift` | +1 property, +1 line in captureOutput, +1 method |
| `ios/ViewContoller/CameraView.swift` | +1 method |
| `ios/TsMediapipeViewManager.m` | +1 exported method |
| `src/index.tsx` or similar | +types and JS binding |

**Total new code: ~50 lines**

---

## What NOT To Do

- ❌ Do not rename any existing files
- ❌ Do not change the folder structure
- ❌ Do not modify pose detection logic
- ❌ Do not add new npm dependencies
- ❌ Do not add new CocoaPods dependencies
- ❌ Do not change the existing `onLandmark` callback behavior
- ❌ Do not refactor or "clean up" existing code
- ❌ Do not add excessive comments or documentation

---

## Success Criteria

The implementation is complete when:

1. Calling `capturePhoto(mediaPipeRef)` returns `{ uri: "file://...", path: "/..." }`
2. The returned file is a valid JPEG image
3. The image matches what the camera is currently showing
4. Pose detection continues to work during and after capture
5. No visible camera flicker or interruption occurs
6. The capture completes in <100ms
