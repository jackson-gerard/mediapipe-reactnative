#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(MediaPipeModule, NSObject)

RCT_EXTERN_METHOD(capturePhoto:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

@end
