# Fusion Node App

A secure device-to-device communication application that uses QR codes for secure key exchange and connection establishment.

## Features

### QR Code Scanning
- **Camera Scanning**: Real-time QR code scanning using the device camera
- **Gallery Upload**: Upload QR code images from your device gallery
- **Secure Validation**: Validates QR codes to ensure they contain valid fusion node data
- **Error Handling**: Comprehensive error handling for invalid or corrupted QR codes

### Connection Types
- **Bluetooth Low Energy (BLE)**: Power-efficient, short-range connections
- **Wi-Fi**: High-bandwidth, longer-range connections

### Security Features
- End-to-end encryption
- Secure key exchange via QR codes
- Time-limited QR codes for enhanced security
- Fusion node relay for secure communication

## How to Use

### QR Code Scanning

#### Option 1: Camera Scanning
1. Navigate to the QR Generation screen
2. Tap "Scan QR" button
3. Point your camera at the QR code
4. The app will automatically detect and process the QR code

#### Option 2: Gallery Upload
1. Navigate to the QR Generation screen
2. Tap "Upload QR from Gallery" button
3. Select an image containing a QR code from your gallery
4. The app will process the image and extract the QR code data

### Supported QR Code Formats
The app supports QR codes with the following format:
```
fusion:publicKey:nodeId:timestamp
```

Example:
```
fusion:abc123:device456:1703123456789
```

### Error Handling
- **No QR Code Found**: Displayed when the selected image doesn't contain a valid QR code
- **Invalid Format**: Displayed when the QR code doesn't match the expected fusion format
- **Processing Error**: Displayed when there's an issue processing the image

## Technical Details

### Dependencies
- **ML Kit Barcode Scanning**: For QR code detection and processing
- **CameraX**: For camera preview and image analysis
- **ZXing**: For QR code generation
- **Coroutines**: For asynchronous image processing

### Permissions
- Camera access for QR code scanning
- Gallery access for image upload (handled by system content provider)

## Development

### Building the Project
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run on a device or emulator

### Testing QR Code Upload
1. Generate a QR code using the app
2. Take a screenshot or save the QR code image
3. Use the "Upload QR from Gallery" feature to test the functionality

## Security Considerations
- QR codes are time-limited for enhanced security
- All communication is end-to-end encrypted
- Fusion node relay ensures secure data transmission
- No sensitive data is stored locally
