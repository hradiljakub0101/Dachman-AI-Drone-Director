# DJI SDK Integration Guide

## Bundle Identifier
**Exact Bundle Identifier:** `cz.dachman.drone.director`

This identifier must match in:
- Apple Developer Account provisioning profile
- Xcode project build settings
- iPhone app installation

## Required Credentials & Configuration

### 1. DJI App Key (REQUIRED for live device)
**Provider:** DJI Developer Account (https://developer.dji.com)

**How to supply:**
- **Method A (Recommended for CI/CD):** Set environment variable before build
  ```bash
  export DJI_APP_KEY="your_64_char_hex_key_here"
  xcodebuild -scheme DachmanDroneDirector build
  ```

- **Method B (Device signing):** Replace `__DJI_APP_KEY__` in `iOSApp/DachmanDroneDirector/Info.plist` with your actual key
  - ⚠️ **DO NOT commit the real key to Git**
  - Use a build script or Xcode Run Script build phase to inject it

### 2. Apple Developer Certificate & Provisioning Profile
**For signed iPhone device build:**

- Apple Developer Team ID (e.g., `ABCD123456`)
- Provisioning profile for bundle ID `cz.dachman.drone.director`
- Development or Distribution certificate

**Update in Xcode or project.yml:**
```yaml
settings:
  base:
    DEVELOPMENT_TEAM: "YOUR_TEAM_ID_HERE"
    CODE_SIGN_STYLE: Automatic  # or Manual if using specific certificate
```

### 3. Device UUID
**For initial testing on a single device:**
- Get iPhone UDID via Xcode Organizer or iTunes
- Add to provisioning profile in Apple Developer Account

## Secure Key Management

### For GitHub Actions CI/CD:
1. Add DJI App Key as GitHub Secret: `DJI_APP_KEY`
2. Update workflow to pass it:
   ```yaml
   - name: Build iOS app
     env:
       DJI_APP_KEY: ${{ secrets.DJI_APP_KEY }}
     run: xcodebuild ...
   ```

### For Local Development:
1. Create `.env.local` (not committed):
   ```bash
   export DJI_APP_KEY="your_key"
   ```
2. Source it before building:
   ```bash
   source .env.local
   xcodebuild ...
   ```

## Signing Settings Status

### Current Configuration:
✓ Bundle ID: `cz.dachman.drone.director`  
✓ Deployment Target: iOS 16.0  
✓ Code Sign Style: Automatic (ready for Team ID)  
✓ Script Sandboxing: Disabled (required for DJI SDK)  
⚠️ Development Team: **Empty** (set before device signing)  

### Next Steps to Enable Device Signing:
1. Enroll Apple Developer Program ($99/year)
2. Create provisioning profile for `cz.dachman.drone.director`
3. Set `DEVELOPMENT_TEAM` in project.yml
4. Provide DJI App Key via environment variable or build phase

## Safety Architecture

✅ **SafetySupervisor:** Fully active - all velocity & safety limits enforced  
✅ **Pilot Override:** Always available via remote controller  
✅ **Simulation Mode:** Enabled by default in DJIStatusView  
✅ **Autonomous Flight:** Requires explicit user approval + valid SafetyContext  

**Mode:** DJI connection shows status only. No autonomous commands sent unless user approves in flight director UI.
