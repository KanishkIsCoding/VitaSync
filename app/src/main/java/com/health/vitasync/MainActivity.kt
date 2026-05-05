package com.health.vitasync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.health.vitasync.ui.theme.VitaSyncTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── Data model ────────────────────────────────────────────────────────────────
data class HealthData(
    val steps: List<StepsRecord> = emptyList(),
    val heartRate: List<HeartRateRecord> = emptyList(),
    val weight: List<WeightRecord> = emptyList(),
    val sleep: List<SleepSessionRecord> = emptyList(),
    val calories: List<TotalCaloriesBurnedRecord> = emptyList(),
    val distance: List<DistanceRecord> = emptyList(),
    val oxygenSaturation: List<OxygenSaturationRecord> = emptyList(),
    val bloodPressure: List<BloodPressureRecord> = emptyList(),
    val heartRateVariability: List<HeartRateVariabilityRmssdRecord> = emptyList()
)

// ── Activity ──────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VitaSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HealthConnectScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// ── Date Range Quick Select Options ──────────────────────────────────────────
enum class DateRangeOption(val label: String, val days: Long) {
    LAST_7_DAYS("Last 7 Days", 7),
    LAST_30_DAYS("Last 30 Days", 30),
    LAST_90_DAYS("Last 90 Days", 90),
    LAST_6_MONTHS("Last 6 Months", 180),
    LAST_1_YEAR("Last 1 Year", 365),
    CUSTOM("Custom", 0)
}

// ── Main Screen ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusMessage by remember { mutableStateOf("Select a date range and press 'Fetch Health Data'.") }
    var isLoading by remember { mutableStateOf(false) }
    var healthData by remember { mutableStateOf<HealthData?>(null) }

    var selectedOption by remember { mutableStateOf(DateRangeOption.LAST_30_DAYS) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var endDate by remember { mutableStateOf(LocalDate.now()) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        scope.launch {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(requiredPermissions)) {
                statusMessage = "Permissions granted! Fetching data..."
                fetchHealthData(context, startDate, endDate) { data, msg ->
                    healthData = data
                    statusMessage = msg
                    isLoading = false
                }
            } else {
                statusMessage = "Some permissions were denied. Please grant all."
                isLoading = false
            }
        }
    }

    // ── Start Date Picker ─────────────────────────────────────────────────────
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        if (picked.isBefore(endDate) || picked.isEqual(endDate)) {
                            startDate = picked
                            selectedOption = DateRangeOption.CUSTOM
                        } else {
                            Toast.makeText(context, "Start date must be before end date", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // ── End Date Picker ───────────────────────────────────────────────────────
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        if (picked.isAfter(startDate) || picked.isEqual(startDate)) {
                            endDate = picked
                            selectedOption = DateRangeOption.CUSTOM
                        } else {
                            Toast.makeText(context, "End date must be after start date", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "Health Connect Export", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = statusMessage, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text(text = "Quick Select", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedOption.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date Range") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DateRangeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                selectedOption = option
                                expanded = false
                                if (option != DateRangeOption.CUSTOM) {
                                    endDate = LocalDate.now()
                                    startDate = LocalDate.now().minusDays(option.days)
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Text(text = "Custom Range", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("From", fontSize = 11.sp)
                        Text(startDate.format(dateFormatter), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("To", fontSize = 11.sp)
                        Text(endDate.format(dateFormatter), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "📅 ${startDate.format(dateFormatter)} → ${endDate.format(dateFormatter)}  ($days days)",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        item {
            Button(
                onClick = {
                    isLoading = true
                    statusMessage = "Checking Health Connect..."
                    scope.launch {
                        val status = HealthConnectClient.getSdkStatus(context)
                        when (status) {
                            HealthConnectClient.SDK_AVAILABLE -> {
                                val client = HealthConnectClient.getOrCreate(context)
                                val granted = client.permissionController.getGrantedPermissions()
                                if (granted.containsAll(requiredPermissions)) {
                                    statusMessage = "Fetching data..."
                                    fetchHealthData(context, startDate, endDate) { data, msg ->
                                        healthData = data
                                        statusMessage = msg
                                        isLoading = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        try {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setPackage("com.google.android.apps.healthdata")
                                                    data = Uri.parse("health-connect://androidx.health.ACTION_REQUEST_PERMISSIONS")
                                                    putExtra("androidx.health.EXTRA_PERMISSIONS", requiredPermissions.toTypedArray())
                                                }
                                                permissionLauncher.launch(intent)
                                            } else {
                                                val intent = Intent().apply {
                                                    component = android.content.ComponentName(
                                                        "com.google.android.apps.healthdata",
                                                        "com.google.android.apps.healthdata.core.permissions.ui.PermissionRequestActivity"
                                                    )
                                                    putStringArrayListExtra("androidx.health.EXTRA_PERMISSIONS", ArrayList(requiredPermissions))
                                                    putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
                                                }
                                                try {
                                                    permissionLauncher.launch(intent)
                                                } catch (e2: Exception) {
                                                    val fallback = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                                                    if (fallback != null) {
                                                        context.startActivity(fallback)
                                                        statusMessage = "Please grant permissions manually in Health Connect app."
                                                    } else {
                                                        statusMessage = "Please install Health Connect from Play Store."
                                                    }
                                                    isLoading = false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            val fallback = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
                                            if (fallback != null) {
                                                context.startActivity(fallback)
                                                statusMessage = "Please grant permissions manually in Health Connect app."
                                            } else {
                                                statusMessage = "Please install Health Connect from Play Store."
                                            }
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                            HealthConnectClient.SDK_UNAVAILABLE -> {
                                statusMessage = "Health Connect not available on this device."
                                isLoading = false
                            }
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                statusMessage = "Please update Health Connect from Play Store."
                                isLoading = false
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Loading..." else "Fetch Health Data")
            }
        }

        if (healthData != null) {
            item { HealthSummaryCard("Steps Records", healthData!!.steps.size) }
            item { HealthSummaryCard("Heart Rate Records", healthData!!.heartRate.size) }
            item { HealthSummaryCard("HRV Records", healthData!!.heartRateVariability.size) }
            item { HealthSummaryCard("SpO2 Records", healthData!!.oxygenSaturation.size) }
            item { HealthSummaryCard("Blood Pressure Records", healthData!!.bloodPressure.size) }
            item { HealthSummaryCard("Sleep Records", healthData!!.sleep.size) }
            item { HealthSummaryCard("Calories Records", healthData!!.calories.size) }
            item { HealthSummaryCard("Distance Records", healthData!!.distance.size) }
            item { HealthSummaryCard("Weight Records", healthData!!.weight.size) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val file = exportToExcel(context, healthData!!, startDate, endDate)
                            if (file != null) {
                                shareFile(context, file)
                            } else {
                                Toast.makeText(context, "Failed to create Excel file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Download Excel (.xlsx)")
                }
            }
        }
    }
}

// ── Summary Card ──────────────────────────────────────────────────────────────
@Composable
fun HealthSummaryCard(label: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontWeight = FontWeight.Medium)
            Text(text = "$count records", color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Fetch Health Data ─────────────────────────────────────────────────────────
suspend fun fetchHealthData(
    context: Context,
    startDate: LocalDate,
    endDate: LocalDate,
    onComplete: suspend (HealthData, String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val client = HealthConnectClient.getOrCreate(context)
        val startTime = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endTime = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val timeRange = TimeRangeFilter.between(startTime, endTime)

        val steps = client.readRecords(ReadRecordsRequest(StepsRecord::class, timeRange)).records
        val heartRate = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, timeRange)).records
        val weight = client.readRecords(ReadRecordsRequest(WeightRecord::class, timeRange)).records
        val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRange)).records
        val calories = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRange)).records
        val distance = client.readRecords(ReadRecordsRequest(DistanceRecord::class, timeRange)).records
        val oxygenSaturation = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)).records
        val bloodPressure = client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, timeRange)).records
        val heartRateVariability = client.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, timeRange)).records

        val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
        onComplete(
            HealthData(steps, heartRate, weight, sleep, calories, distance, oxygenSaturation, bloodPressure, heartRateVariability),
            "Loaded $days days of data successfully!"
        )
    } catch (e: Exception) {
        onComplete(HealthData(), "Error fetching data: ${e.message}")
    }
}

// ── Helper: create styled header row ─────────────────────────────────────────
fun createHeaderRow(sheet: Sheet, workbook: XSSFWorkbook, headers: List<String>) {
    val headerStyle = workbook.createCellStyle().apply {
        fillForegroundColor = IndexedColors.DARK_TEAL.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
        val font = workbook.createFont().apply {
            color = IndexedColors.WHITE.index
            bold = true
        }
        setFont(font)
        alignment = HorizontalAlignment.CENTER
    }
    val row = sheet.createRow(0)
    headers.forEachIndexed { i, header ->
        row.createCell(i).apply {
            setCellValue(header)
            cellStyle = headerStyle
        }
        sheet.setColumnWidth(i, 22 * 256)
    }
}

// ── Export to Excel with multiple sheets ──────────────────────────────────────
suspend fun exportToExcel(
    context: Context,
    data: HealthData,
    startDate: LocalDate,
    endDate: LocalDate
): File? = withContext(Dispatchers.IO) {
    try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val file = File(
            context.cacheDir,
            "vitasync_${startDate.format(dateFormatter)}_to_${endDate.format(dateFormatter)}.xlsx"
        )

        val workbook = XSSFWorkbook()

        // ── Sheet 1: Summary ──────────────────────────────────────────────────
        val summarySheet = workbook.createSheet("Summary")
        createHeaderRow(summarySheet, workbook, listOf("Data Type", "Record Count", "Period"))
        val periodStr = "${startDate.format(dateFormatter)} to ${endDate.format(dateFormatter)}"
        listOf(
            listOf("Steps", data.steps.size.toString(), periodStr),
            listOf("Heart Rate", data.heartRate.sumOf { it.samples.size }.toString(), periodStr),
            listOf("HRV", data.heartRateVariability.size.toString(), periodStr),
            listOf("SpO2", data.oxygenSaturation.size.toString(), periodStr),
            listOf("Blood Pressure", data.bloodPressure.size.toString(), periodStr),
            listOf("Sleep", data.sleep.size.toString(), periodStr),
            listOf("Calories", data.calories.size.toString(), periodStr),
            listOf("Distance", data.distance.size.toString(), periodStr),
            listOf("Weight", data.weight.size.toString(), periodStr)
        ).forEachIndexed { i, row ->
            val sheetRow = summarySheet.createRow(i + 1)
            row.forEachIndexed { j, value -> sheetRow.createCell(j).setCellValue(value) }
        }

        // ── Sheet 2: Steps ────────────────────────────────────────────────────
        val stepsSheet = workbook.createSheet("Steps")
        createHeaderRow(stepsSheet, workbook, listOf("Date", "Start Time", "End Time", "Step Count"))
        data.steps.forEachIndexed { i, record ->
            val row = stepsSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.startTime).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.startTime).substring(11))
            row.createCell(2).setCellValue(formatter.format(record.endTime).substring(11))
            row.createCell(3).setCellValue(record.count.toDouble())
        }

        // ── Sheet 3: Heart Rate ───────────────────────────────────────────────
        val hrSheet = workbook.createSheet("Heart Rate")
        createHeaderRow(hrSheet, workbook, listOf("Date", "Time", "BPM"))
        var hrRowIdx = 1
        data.heartRate.forEach { record ->
            record.samples.forEach { sample ->
                val row = hrSheet.createRow(hrRowIdx++)
                row.createCell(0).setCellValue(formatter.format(sample.time).substring(0, 10))
                row.createCell(1).setCellValue(formatter.format(sample.time).substring(11))
                row.createCell(2).setCellValue(sample.beatsPerMinute.toDouble())
            }
        }

        // ── Sheet 4: HRV ─────────────────────────────────────────────────────
        val hrvSheet = workbook.createSheet("HRV")
        createHeaderRow(hrvSheet, workbook, listOf("Date", "Time", "HRV RMSSD (ms)"))
        data.heartRateVariability.forEachIndexed { i, record ->
            val row = hrvSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.time).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.time).substring(11))
            row.createCell(2).setCellValue(record.heartRateVariabilityMillis)
        }

        // ── Sheet 5: SpO2 ─────────────────────────────────────────────────────
        val spo2Sheet = workbook.createSheet("SpO2")
        createHeaderRow(spo2Sheet, workbook, listOf("Date", "Time", "SpO2 (%)"))
        data.oxygenSaturation.forEachIndexed { i, record ->
            val row = spo2Sheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.time).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.time).substring(11))
            row.createCell(2).setCellValue(record.percentage.value)
        }

        // ── Sheet 6: Blood Pressure ───────────────────────────────────────────
        val bpSheet = workbook.createSheet("Blood Pressure")
        createHeaderRow(bpSheet, workbook, listOf("Date", "Time", "Systolic (mmHg)", "Diastolic (mmHg)"))
        data.bloodPressure.forEachIndexed { i, record ->
            val row = bpSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.time).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.time).substring(11))
            row.createCell(2).setCellValue(record.systolic.inMillimetersOfMercury)
            row.createCell(3).setCellValue(record.diastolic.inMillimetersOfMercury)
        }

        // ── Sheet 7: Sleep ────────────────────────────────────────────────────
        val sleepSheet = workbook.createSheet("Sleep")
        createHeaderRow(sleepSheet, workbook, listOf("Date", "Start Time", "End Time", "Duration (hours)"))
        data.sleep.forEachIndexed { i, record ->
            val row = sleepSheet.createRow(i + 1)
            val hours = ChronoUnit.MINUTES.between(record.startTime, record.endTime) / 60.0
            row.createCell(0).setCellValue(formatter.format(record.startTime).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.startTime).substring(11))
            row.createCell(2).setCellValue(formatter.format(record.endTime).substring(11))
            row.createCell(3).setCellValue("%.2f".format(hours).toDouble())
        }

        // ── Sheet 8: Calories ─────────────────────────────────────────────────
        val caloriesSheet = workbook.createSheet("Calories")
        createHeaderRow(caloriesSheet, workbook, listOf("Date", "Start Time", "End Time", "Calories (kcal)"))
        data.calories.forEachIndexed { i, record ->
            val row = caloriesSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.startTime).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.startTime).substring(11))
            row.createCell(2).setCellValue(formatter.format(record.endTime).substring(11))
            row.createCell(3).setCellValue(record.energy.inKilocalories)
        }

        // ── Sheet 9: Distance ─────────────────────────────────────────────────
        val distanceSheet = workbook.createSheet("Distance")
        createHeaderRow(distanceSheet, workbook, listOf("Date", "Start Time", "End Time", "Distance (km)"))
        data.distance.forEachIndexed { i, record ->
            val row = distanceSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.startTime).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.startTime).substring(11))
            row.createCell(2).setCellValue(formatter.format(record.endTime).substring(11))
            row.createCell(3).setCellValue(record.distance.inKilometers)
        }

        // ── Sheet 10: Weight ──────────────────────────────────────────────────
        val weightSheet = workbook.createSheet("Weight")
        createHeaderRow(weightSheet, workbook, listOf("Date", "Time", "Weight (kg)"))
        data.weight.forEachIndexed { i, record ->
            val row = weightSheet.createRow(i + 1)
            row.createCell(0).setCellValue(formatter.format(record.time).substring(0, 10))
            row.createCell(1).setCellValue(formatter.format(record.time).substring(11))
            row.createCell(2).setCellValue(record.weight.inKilograms)
        }

        // ── Write file ────────────────────────────────────────────────────────
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        file

    } catch (e: Exception) {
        null
    }
}

// ── Share / Download Excel ────────────────────────────────────────────────────
fun shareFile(context: Context, file: File) {
    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Save VitaSync Excel File"))
}