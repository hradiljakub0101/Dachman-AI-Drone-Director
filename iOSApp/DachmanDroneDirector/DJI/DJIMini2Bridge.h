#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^DachmanStatusBlock)(NSString *status);
typedef void (^DachmanPilotOverrideBlock)(void);

@interface DJIMini2Bridge : NSObject
@property(nonatomic, copy, nullable) DachmanStatusBlock statusBlock;
@property(nonatomic, copy, nullable) DachmanPilotOverrideBlock pilotOverrideBlock;
@property(nonatomic, readonly) BOOL virtualStickEnabled;
- (void)registerSDK;
- (void)enableVirtualStick;
- (void)disableVirtualStick;
- (void)sendPitch:(float)pitch roll:(float)roll yawRate:(float)yawRate vertical:(float)vertical;
@end

NS_ASSUME_NONNULL_END
