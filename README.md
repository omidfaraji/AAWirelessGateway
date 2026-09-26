# AA Wireless Gateway

AA Wireless Gateway turns a spare Android phone into an adapter for cars that support wired,
but not wireless, Android Auto.

The spare **gateway phone** connects to the car by USB. Your **main phone** connects to the
gateway over Bluetooth and Wi-Fi, allowing Android Auto to appear wirelessly on the car display.

> This is an experimental community project. Compatibility depends on the phones, Android
> versions, USB cable, and vehicle head unit.

## Requirements

- A car with working wired Android Auto
- A spare phone running Android 8.0 or newer
- A main phone that supports wireless Android Auto
- Bluetooth pairing between the two phones
- A reliable USB data cable

Root is **not required** for the recommended setup. It is only used by the optional fallback that
starts wired Android Auto directly on the gateway phone.

## Recommended setup

Install the app only on the gateway phone:

1. Pair the gateway phone with the main phone in Android Bluetooth settings.
2. Open AA Wireless Gateway and enable **USB gateway - spare phone**.
3. Select the main phone under **Android Auto phone**.
4. Keep **Native wireless Android Auto** enabled.
5. Grant every permission shown under **Permissions and pairing**.
6. Confirm the status card says **Ready to connect**.
7. Enable Bluetooth, Wi-Fi, and wireless Android Auto on the main phone.
8. Connect the gateway phone to the car's Android Auto USB port.
9. If Android asks which app should handle the USB device, choose **AA Wireless Gateway** and
   **Always**.

The first connection may take one or two minutes while permission and pairing prompts are
completed. Later connections normally take 15 to 60 seconds.

## Legacy two-app setup

Use this only if the native connection does not work:

1. Install the app on both phones.
2. On the spare phone, enable **USB gateway - spare phone** and disable
   **Native wireless Android Auto**.
3. Configure the manual hotspot details and select the main phone.
4. On the main phone, enable **Wireless client - main phone**.
5. Enter the gateway hotspot details and select the gateway phone.
6. Grant all requested permissions until both apps report **Ready to connect**.

## How it works

In native mode, the gateway:

1. Starts when the car opens an Android Auto USB accessory connection.
2. Creates a local-only Wi-Fi hotspot with generated credentials.
3. Sends those credentials to the selected main phone through the Android Auto Bluetooth
   handshake.
4. Forwards Android Auto traffic between Wi-Fi and the car's USB connection.

Android 11 and newer hotspot behaviors are handled automatically, including dynamic hotspot
addresses and systems that hide network-interface details. An optional gateway IP fallback is
available for devices where Android prevents automatic discovery.

## Troubleshooting

| Problem | What to check |
|---|---|
| The app does not open after USB connection | Use the car's Android Auto USB port and a data-capable cable. |
| The app says **Setup needed** | Follow the exact action displayed in the status card. |
| Hotspot creation fails on Android 12 or older | Grant precise location and **Allow all the time** location access. |
| The main phone does not connect | Enable Bluetooth and Wi-Fi, confirm the phones are paired, and enable wireless Android Auto. |
| Connection stops when the screen turns off | Set battery usage for the app to **Unrestricted** on the gateway phone. |
| Connection repeatedly fails | Unplug USB, stop the app, verify both phones still show **Ready to connect**, and try again. |

For debugging, capture logs immediately after a failed attempt:

    adb logcat -s AAService

## Build

The project requires Android SDK 36 and Java 17.

    ./gradlew assembleDebug

The debug APK is generated at:

    app/build/outputs/apk/debug/app-debug.apk

Run the automated checks with:

    ./gradlew testDebugUnitTest lintDebug

Hardware hotspot behavior can be checked on a connected Android device with:

    ./gradlew connectedDebugAndroidTest

## Compatibility

- Minimum Android version: Android 8.0, API 26
- Target Android version: Android 16, API 36
- Tested gateway devices:
  - Samsung Galaxy A70, Android 11
  - Samsung Galaxy S23 Ultra, Android 16

Not every vehicle or Android device combination is guaranteed to work.

## Safety

Complete setup and troubleshooting while parked. Do not interact with either phone while driving.

## License

The upstream project currently has no declared software license. Public source availability does
not by itself grant permission to redistribute APKs or publish derivative releases. Obtain
permission from the upstream author before distributing compiled builds.

## Credits

This fork is based on
[nisargjhaveri/AAWirelessGateway](https://github.com/nisargjhaveri/AAWirelessGateway) and was
inspired by [borconi/AAGateWay](https://github.com/borconi/AAGateWay).
