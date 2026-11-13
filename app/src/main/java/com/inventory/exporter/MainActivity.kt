package com.inventory.exporter

import android.content.Intent
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
        setContent {
            MaterialTheme { ExportScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen() {
    val context = LocalContext.current
    var savedPath by remember { mutableStateOf<String?>(null) }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val wb = XSSFWorkbook()
                    val sheet = wb.createSheet("Сканирования")
                    val header = sheet.createRow(0)
                    listOf("Штрих-код", "Количество", "Формат", "Дата/Время")
                        .forEachIndexed { i, t -> header.createCell(i).setCellValue(t) }
                    val df = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                    for (i in 1..10) {
                        val row = sheet.createRow(i)
                        row.createCell(0).setCellValue("460372690596$i")
                        row.createCell(1).setCellValue(i.toDouble())
                        row.createCell(2).setCellValue("BARCODE")
                        row.createCell(3).setCellValue(df.format(Date()))
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
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
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
