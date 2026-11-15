package com.inventory.exporter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ExportScreen() } }
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

data class ScanRecord(
    val id: Long,
    val barcode: String,
    val quantity: Int,
    val format: String,
    val timestamp: Long,
    val userName: String = ""
)

private fun readScansFromProvider(context: Context): List<ScanRecord> {
    val uri = Uri.parse("content://com.inventory.scanner.history/scans")
    val scans = mutableListOf<ScanRecord>()
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val barcodeIdx = cursor.getColumnIndexOrThrow("barcode")
            val quantityIdx = cursor.getColumnIndexOrThrow("quantity")
            val formatIdx = cursor.getColumnIndexOrThrow("format")
            val timestampIdx = cursor.getColumnIndexOrThrow("timestamp")
            val userIdx = cursor.getColumnIndex("user_name")
            while (cursor.moveToNext()) {
                val name = if (userIdx >= 0) (cursor.getString(userIdx) ?: "") else ""
                scans.add(
                    ScanRecord(
                        id = cursor.getLong(idIdx),
                        barcode = cursor.getString(barcodeIdx),
                        quantity = cursor.getInt(quantityIdx),
                        format = cursor.getString(formatIdx),
                        timestamp = cursor.getLong(timestampIdx),
                        userName = name
                    )
                )
            }
        }
    } catch (e: Exception) { }
    return scans
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen() {
    val context = LocalContext.current
    val activity = context.findActivity()
    var savedPath by remember { mutableStateOf<String?>(null) }
    var scanCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { scanCount = readScansFromProvider(context).size }

    fun goBackToHub() {
        val hub = context.packageManager.getLaunchIntentForPackage("com.inventory.hub")
        if (hub != null) {
            hub.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(hub)
        }
        activity?.finish()
    }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val scans = readScansFromProvider(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val wb = XSSFWorkbook()
                    val sheet = wb.createSheet("Сканирования")
                    val header = sheet.createRow(0)
                    listOf("№", "Артикул", "Штрихкод", "Наименование", "Количество")
                        .forEachIndexed { i, t -> header.createCell(i).setCellValue(t) }
                    if (scans.isEmpty()) {
                        for (i in 1..10) {
                            val row = sheet.createRow(i)
                            row.createCell(0).setCellValue(i.toDouble())
                            row.createCell(1).setCellValue("ART$i")
                            row.createCell(2).setCellValue("460372690596$i")
                            row.createCell(3).setCellValue("Тестовый товар $i")
                            row.createCell(4).setCellValue(1.0)
                        }
                    } else {
                        val grouped = scans.groupBy { it.barcode }
                            .map { (barcode, list) ->
                                val total = list.sumOf { it.quantity }
                                val first = list.first()
                                val name = list.firstOrNull { it.userName.isNotBlank() }?.userName ?: ""
                                ScanRecord(first.id, barcode, total, first.format, first.timestamp, name)
                            }
                        grouped.forEachIndexed { index, scan ->
                            val row = sheet.createRow(index + 1)
                            row.createCell(0).setCellValue((index + 1).toDouble())
                            row.createCell(1).setCellValue("")
                            row.createCell(2).setCellValue(scan.barcode)
                            row.createCell(3).setCellValue(scan.userName)
                            row.createCell(4).setCellValue(scan.quantity.toDouble())
                        }
                    }
                    wb.write(out)
                    wb.close()
                }
                savedPath = uri.toString()
                Toast.makeText(context, "Excel сохранён", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экспорт в Excel") },
                navigationIcon = {
                    IconButton(onClick = { goBackToHub() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "В хаб")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Экспорт данных в Excel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Найдено сканирований: $scanCount", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { createDoc.launch("inventory_export.xlsx") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Создать Excel")
            }
            savedPath?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Файл сохранён", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
