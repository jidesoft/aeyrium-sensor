#import "AeyriumSensorPlugin.h"
#import <CoreMotion/CoreMotion.h>
#import <GLKit/GLKit.h>

@implementation AeyriumSensorPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FLTSensorStreamHandler* sensorStreamHandler =
      [[FLTSensorStreamHandler alloc] init];
  FlutterEventChannel* sensorChannel =
      [FlutterEventChannel eventChannelWithName:@"plugins.aeyrium.com/sensor"
                                binaryMessenger:[registrar messenger]];
  [sensorChannel setStreamHandler:sensorStreamHandler];
}

@end

CMMotionManager* _motionManager;

void _initMotionManager() {
  if (!_motionManager) {
    _motionManager = [[CMMotionManager alloc] init];
    _motionManager.deviceMotionUpdateInterval = 0.03;
  }
}

static void sendData(Float64 pitch, Float64 roll, Float64 azimuth, Float64 accuracy, FlutterEventSink sink) {
  NSMutableData* event = [NSMutableData dataWithCapacity:3 * sizeof(Float64)];
  [event appendBytes:&pitch length:sizeof(Float64)];
  [event appendBytes:&roll length:sizeof(Float64)];
  [event appendBytes:&azimuth length:sizeof(Float64)];
  [event appendBytes:&accuracy length:sizeof(Float64)];
  sink([FlutterStandardTypedData typedDataWithFloat64:event]);
}


@implementation FLTSensorStreamHandler

double degrees(double radians) {
  return (180/M_PI) * radians;
}

- (FlutterError*)onListenWithArguments:(id)arguments eventSink:(FlutterEventSink)eventSink {
  _initMotionManager();
   [_motionManager
   startDeviceMotionUpdatesUsingReferenceFrame:CMAttitudeReferenceFrameXArbitraryCorrectedZVertical toQueue:[[NSOperationQueue alloc] init]
   withHandler:^(CMDeviceMotion* data, NSError* error) {
      CMAttitude *attitude = data.attitude;
     CMQuaternion quat = attitude.quaternion;
   
     CMDeviceMotion *deviceMotion = data;

     double pitch = attitude.pitch, roll = attitude.roll, azimuth = attitude.yaw;
     
     // Correct for the rotation matrix not including the screen orientation:
     UIDeviceOrientation orientation = [[UIDevice currentDevice] orientation];
     float deviceOrientationRadians = 0.0f;
     if (orientation == UIDeviceOrientationPortrait) {
       deviceOrientationRadians = 0;
     }
     else if (orientation == UIDeviceOrientationLandscapeLeft) {
       deviceOrientationRadians = M_PI_2;
     }
     else if (orientation == UIDeviceOrientationLandscapeRight) {
       deviceOrientationRadians = -M_PI_2;
     }
     else if (orientation == UIDeviceOrientationPortraitUpsideDown) {
       deviceOrientationRadians = M_PI;
     }
     else if (orientation == UIDeviceOrientationFaceDown) {
       deviceOrientationRadians = 0;
     }
     else if (orientation == UIDeviceOrientationFaceUp) {
       deviceOrientationRadians = 0;
     }

     GLKMatrix4 baseRotation = GLKMatrix4MakeRotation(deviceOrientationRadians, 0.0f, 0.0f, 1.0f);

     GLKMatrix4 deviceMotionAttitudeMatrix;
     CMRotationMatrix a = deviceMotion.attitude.rotationMatrix;
     deviceMotionAttitudeMatrix
     = GLKMatrix4Make(a.m11, a.m21, a.m31, 0.0f,
                      a.m12, a.m22, a.m32, 0.0f,
                      a.m13, a.m23, a.m33, 0.0f,
                      0.0f, 0.0f, 0.0f, 1.0f);

     deviceMotionAttitudeMatrix = GLKMatrix4Multiply(baseRotation, deviceMotionAttitudeMatrix);
     pitch = (asin(-deviceMotionAttitudeMatrix.m22));
     double roll1 = -(atan2(2*(quat.y*quat.w - quat.x*quat.z), 1 - 2*quat.y*quat.y - 2*quat.z*quat.z)) ;
     double roll2 = -(atan2(-a.m13, a.m33)); //roll based on android code from matrix
     roll =  atan2(data.gravity.x, data.gravity.y) - M_PI; //roll based on just gravity
     sendData(degrees(pitch), degrees(roll), degrees(deviceOrientationRadians), 0.0, eventSink);
   }];
  return nil;
}

- (FlutterError*)onCancelWithArguments:(id)arguments {
  [_motionManager stopDeviceMotionUpdates];
  return nil;
}

@end
