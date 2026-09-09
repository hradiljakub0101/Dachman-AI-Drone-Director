#import "DJIMini2Bridge.h"
#import <DJISDK/DJISDK.h>

@interface DJIMini2Bridge () <DJISDKManagerDelegate, DJIRemoteControllerDelegate>
@property(nonatomic, readwrite) BOOL virtualStickEnabled;
@property(nonatomic, strong, nullable) DJIFlightController *flightController;
@end

@implementation DJIMini2Bridge

- (void)registerSDK {
    [DJISDKManager registerAppWithDelegate:self];
    if (self.statusBlock) self.statusBlock(@"Registering DJI SDK…");
}

- (void)appRegisteredWithError:(NSError *)error {
    if (error) {
        if (self.statusBlock) self.statusBlock([NSString stringWithFormat:@"DJI registration failed: %@", error.localizedDescription]);
        return;
    }
    if (self.statusBlock) self.statusBlock(@"DJI SDK registered");
    [DJISDKManager startConnectionToProduct];
}

- (void)productConnected:(DJIBaseProduct *)product {
    if (![product isKindOfClass:[DJIAircraft class]]) return;
    DJIAircraft *aircraft = (DJIAircraft *)product;
    self.flightController = aircraft.flightController;
    aircraft.remoteController.delegate = self;
    if (self.statusBlock) self.statusBlock(@"DJI aircraft connected");
}

- (void)productDisconnected {
    self.virtualStickEnabled = NO;
    self.flightController = nil;
    if (self.statusBlock) self.statusBlock(@"DJI aircraft disconnected");
}

- (void)enableVirtualStick {
    DJIFlightController *fc = self.flightController;
    if (!fc) { if (self.statusBlock) self.statusBlock(@"No flight controller"); return; }
    fc.rollPitchControlMode = DJIVirtualStickRollPitchControlModeVelocity;
    fc.yawControlMode = DJIVirtualStickYawControlModeAngularVelocity;
    fc.verticalControlMode = DJIVirtualStickVerticalControlModeVelocity;
    fc.rollPitchCoordinateSystem = DJIVirtualStickFlightCoordinateSystemBody;
    fc.isVirtualStickAdvancedModeEnabled = YES;
    __weak typeof(self) weakSelf = self;
    [fc setVirtualStickModeEnabled:YES withCompletion:^(NSError * _Nullable error) {
        weakSelf.virtualStickEnabled = (error == nil);
        if (weakSelf.statusBlock) weakSelf.statusBlock(error ? [NSString stringWithFormat:@"Virtual Stick error: %@", error.localizedDescription] : @"Virtual Stick enabled");
    }];
}

- (void)disableVirtualStick {
    DJIFlightController *fc = self.flightController;
    if (!fc) { self.virtualStickEnabled = NO; return; }
    __weak typeof(self) weakSelf = self;
    [fc setVirtualStickModeEnabled:NO withCompletion:^(NSError * _Nullable error) {
        weakSelf.virtualStickEnabled = NO;
        if (weakSelf.statusBlock) weakSelf.statusBlock(error ? [NSString stringWithFormat:@"Disable error: %@", error.localizedDescription] : @"Virtual Stick disabled");
    }];
}

- (void)sendPitch:(float)pitch roll:(float)roll yawRate:(float)yawRate vertical:(float)vertical {
    if (!self.virtualStickEnabled || !self.flightController) return;
    DJIVirtualStickFlightControlData data;
    data.pitch = pitch; data.roll = roll; data.yaw = yawRate; data.verticalThrottle = vertical;
    [self.flightController sendVirtualStickFlightControlData:data withCompletion:nil];
}

- (void)remoteController:(DJIRemoteController *)rc didUpdateHardwareState:(DJIRCHardwareState)state {
    const int threshold = 90;
    BOOL moved = abs(state.leftStick.horizontalPosition) > threshold ||
                 abs(state.leftStick.verticalPosition) > threshold ||
                 abs(state.rightStick.horizontalPosition) > threshold ||
                 abs(state.rightStick.verticalPosition) > threshold;
    if (moved && self.virtualStickEnabled) {
        [self disableVirtualStick];
        if (self.pilotOverrideBlock) self.pilotOverrideBlock();
        if (self.statusBlock) self.statusBlock(@"PILOT OVERRIDE – physical stick moved");
    }
}

@end
