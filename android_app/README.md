# Smart Food Monitor - Android App

This folder contains a starter Jetpack Compose `MainActivity.kt` file using Material You (Material 3) design.

## How to use this with Android Studio:

1. **Create a New Project:** Open Android Studio and select **New Project**.
2. **Choose Template:** Select **Empty Activity** (which uses Jetpack Compose by default).
3. **Name:** Name it "Food Monitor".
4. **Copy Code:** Copy the contents of `MainActivity.kt` provided in this folder and replace the generated `MainActivity.kt` in your project (`app/src/main/java/com/example/foodmonitor/MainActivity.kt`).
5. **Add Internet Permission:** Open your `AndroidManifest.xml` (in `app/src/main/AndroidManifest.xml`) and add:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```
6. **Testing:** If your phone is connected to the ESP32's Access Point (`FoodMonitor-XXXX`), the app will immediately pull data from `http://192.168.4.1/data`. If using a local Wi-Fi router, replace the IP in the code with the ESP32's assigned IP.

## Adding BLE (Bluetooth Low Energy) Support
The ESP32 code is already advertising a Nordic UART BLE Service (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`).
To connect to it from Android when Wi-Fi is down:
1. Add BLE permissions to `AndroidManifest.xml` (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`).
2. Use Android's `BluetoothLeScanner` to scan for the device name `FoodMonitor`.
3. Connect using `BluetoothGatt`, discover services, and subscribe to the TX characteristic (`6e400003-b5a3-f393-e0a9-e50e24dcca9e`) to receive the JSON strings just like the HTTP endpoint.
4. (Optional) You can use a library like [Nordic Android-BLE Library](https://github.com/NordicSemiconductor/Android-BLE-Library) for much easier Bluetooth integration.

Alternatively, you can test the BLE functionality immediately without writing code by downloading **nRF Connect for Mobile** or **Serial Bluetooth Terminal** from the Play Store, connecting to "FoodMonitor", and listening to the TX characteristic!
