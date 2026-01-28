import Foundation
import React

class CameraViewRegistry {
    static let shared = CameraViewRegistry()
    weak var activeCameraView: CameraView?

    private init() {}
}

@objc(MediaPipeModule)
class MediaPipeModule: NSObject {

    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func capturePhoto(_ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock) {
        DispatchQueue.main.async {
            guard let cameraView = CameraViewRegistry.shared.activeCameraView else {
                reject("NO_VIEW", "Camera view not available", nil)
                return
            }
            cameraView.capturePhoto(resolve, rejecter: reject)
        }
    }
}
