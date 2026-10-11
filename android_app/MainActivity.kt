package com.example.foodmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// NOTE: You will need to add your downloaded font (e.g., poppins_regular.ttf, poppins_bold.ttf) 
// to the res/font folder in Android Studio for this to work.
// If you don't have it downloaded yet, Android Studio's Downloadable Fonts feature is recommended.
val PoppinsFontFamily = FontFamily(
    // Font(R.font.poppins_regular, FontWeight.Normal),
    // Font(R.font.poppins_bold, FontWeight.Bold)
    androidx.compose.ui.text.font.FontFamily.SansSerif // Fallback for now until font is added
)

val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = PoppinsFontFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = PoppinsFontFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = PoppinsFontFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = PoppinsFontFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = PoppinsFontFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = PoppinsFontFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = PoppinsFontFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = PoppinsFontFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = PoppinsFontFamily),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = PoppinsFontFamily),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = PoppinsFontFamily),
    bodySmall = Typography().bodySmall.copy(fontFamily = PoppinsFontFamily),
    labelLarge = Typography().labelLarge.copy(fontFamily = PoppinsFontFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = PoppinsFontFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = PoppinsFontFamily),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(typography = AppTypography) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FoodMonitorScreen()
                }
            }
        }
    }
}

@Composable
fun FoodMonitorScreen() {
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableStateOf("WAITING") }
    var percentage by remember { mutableStateOf(0) }
    var rawValue by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    var connectionMode by remember { mutableStateOf("Connecting...") }
    
    // Auto-fetch data from Wi-Fi every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                // 192.168.4.1 is the default IP when ESP32 is in AP mode.
                // If it connects to your home Wi-Fi, change this to its assigned IP.
                val result = fetchData("http://192.168.4.1/data")
                val json = JSONObject(result)
                status = json.optString("status", "UNKNOWN")
                percentage = json.optInt("pct", 0)
                rawValue = json.optInt("raw", 0)
                connectionMode = "Wi-Fi Connected"
                errorMessage = ""
            } catch (e: Exception) {
                connectionMode = "Wi-Fi Disconnected"
                errorMessage = "Attempting BLE fallback... (Implement BLE scan here)"
                // BLE fallback logic would be triggered here in a complete BLE implementation
            }
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Smart Food Monitor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 32.dp)
        )
        
        // Status Chip
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (errorMessage.isEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = connectionMode,
                style = MaterialTheme.typography.labelLarge,
                color = if (errorMessage.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Current Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                val statusColor = when (status) {
                    "FRESH" -> Color(0xFF4CAF50)
                    "WARNING" -> Color(0xFFFF9800)
                    "SPOILED" -> Color(0xFFE53935)
                    else -> MaterialTheme.colorScheme.outline
                }
                
                Text(
                    text = status,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "VOC Concentration", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$percentage%", 
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                LinearProgressIndicator(
                    progress = percentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .padding(top = 8.dp),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Raw ADC: $rawValue", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

suspend fun fetchData(urlString: String): String {
    return kotlinx.coroutines.Dispatchers.IO.invoke {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
