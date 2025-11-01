package io.ztech.imcapp

import android.text.Editable
import android.text.TextWatcher
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import android.util.Log

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// (A sua Data Class ImcRecord - sem alteração)
data class ImcRecord(
    val nome: String,
    val data: String,
    val hora: String,
    val peso: Double,
    val altura: Double,
    val imc: Double
)

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val JSON_FILE_NAME = "imc_history.json"
    }

    // (As suas variáveis de View)
    private lateinit var etPeso: TextInputEditText
    private lateinit var etAltura: TextInputEditText
    private lateinit var tvResultado: TextView
    private lateinit var btCalcular: Button
    private lateinit var btLimpar: Button
    private lateinit var etNome: TextInputEditText
    private lateinit var tvResultadoExplicacao: TextView
    private lateinit var imcExplanationLayout: LinearLayout
    private lateinit var imcHistoryLayout: LinearLayout
    private lateinit var historyTableContent: TableLayout

    private val historyList = mutableListOf<ImcRecord>()
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // (O seu código de ToneGenerator e WindowInsets)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) { e.printStackTrace() }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        init()

        // (Os seus listeners de botão)
        btCalcular.setOnClickListener { btCalcularOnClick() }
        btLimpar.setOnClickListener { Toast.makeText(this, "Segure para limpar", Toast.LENGTH_SHORT).show() }
        btLimpar.setOnLongClickListener {
            btLimparOnClick()
            playBeep()
            true
        }
    } // fim do onCreate()

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
    }

    private fun init() {
        // (findViewByIds)
        etPeso = findViewById(R.id.etPeso)
        etAltura = findViewById(R.id.etAltura)
        tvResultado = findViewById(R.id.tvResultado)
        btCalcular = findViewById(R.id.btCalcular)
        btLimpar = findViewById(R.id.btLimpar)
        etNome = findViewById(R.id.etNome)
        tvResultadoExplicacao = findViewById(R.id.tvResultadoExplicacao)
        imcExplanationLayout = findViewById(R.id.imc_explanation_layout)
        imcHistoryLayout = findViewById(R.id.imc_history_layout)
        historyTableContent = findViewById(R.id.history_table_content)

        // (TextWatcher)
        etAltura.addTextChangedListener(HeightTextWatcher(etAltura))

        // Carrega o histórico e atualiza a tabela
        loadHistoryFromJson()
        updateHistoryTable()

        // Listener de Long Click para a caixa de histórico
        imcHistoryLayout.setOnLongClickListener {
            Toast.makeText(this, "A preparar o histórico para partilhar...", Toast.LENGTH_SHORT).show()
            exportHistoryToJson()
            true // Consome o evento
        }
    }

    private fun btCalcularOnClick() {
        // (Validações)
        etPeso.error = null
        etAltura.error = null
        etNome.error = null
        val nomeInput = etNome.text.toString().trim()
        val peso = etPeso.text.toString().toDoubleOrNull()
        val altura = etAltura.text.toString().toDoubleOrNull()
        if (peso == null) { etPeso.error = "Peso deve ser informado."; return }
        if (altura == null) { etAltura.error = "Altura deve ser informada."; return }
        if (altura == 0.0) { etAltura.error = "Altura não pode ser zero."; return }

        // Cálculo
        val resultado = peso / (altura * altura)
        tvResultado.text = "%.2f".format(resultado)

        // Atualiza Caixa 2
        val explicacao = getExplicacaoIMC(resultado, nomeInput)
        tvResultadoExplicacao.text = explicacao
        imcExplanationLayout.visibility = View.VISIBLE

        // (Lógica de salvar)
        val nome = if (nomeInput.isNotBlank()) nomeInput else "N/A"
        val (data, hora) = getCurrentDateTime()
        val record = ImcRecord(nome, data, hora, peso, altura, resultado)
        historyList.add(record)
        saveHistoryToJson()

        // Atualiza a tabela e mostra a caixa
        updateHistoryTable()
        imcHistoryLayout.visibility = View.VISIBLE
    }

    private fun btLimparOnClick() {
        etNome.text?.clear()
        etPeso.text?.clear()
        etAltura.text?.clear()
        tvResultado.text = "-"
        etNome.error = null
        etPeso.error = null
        etAltura.error = null
        etPeso.requestFocus()

        // Esconde as caixas de resultado
        imcExplanationLayout.visibility = View.GONE

        // Limpa a lista, apaga o ficheiro e atualiza a tabela
        historyList.clear()
        deleteHistoryJsonFile()
        updateHistoryTable() // Atualiza a tabela para mostrar "Sem medições"
    }

    // (getExplicacaoIMC - sem alteração)
    private fun getExplicacaoIMC(imc: Double, nome: String): String {
        val classificacao = when {
            imc < 18.5 -> "Abaixo do peso"
            imc < 24.9 -> "Peso normal"
            imc < 29.9 -> "Sobrepeso"
            imc < 34.9 -> "Obesidade Grau I"
            imc < 39.9 -> "Obesidade Grau II"
            else -> "Obesidade Grau III (Mórbida)"
        }
        val prefixo = if (nome.isNotBlank()) "$nome, você está com" else "Você está com"
        return "$prefixo $classificacao."
    }

    // (playBeep - sem alteração)
    private fun playBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Erro ao tocar o beep: ${e.message}")
        }
    }

    // (loadHistoryFromJson - sem alteração)
    private fun loadHistoryFromJson() {
        try {
            val file = File(filesDir, JSON_FILE_NAME)
            if (!file.exists()) return
            val jsonString = file.readText()
            if (jsonString.isBlank()) return
            val jsonArray = JSONArray(jsonString)
            historyList.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                historyList.add(ImcRecord(
                    nome = jsonObject.getString("nome"),
                    data = jsonObject.getString("data"),
                    hora = jsonObject.getString("hora"),
                    peso = jsonObject.getDouble("peso"),
                    altura = jsonObject.getDouble("altura"),
                    imc = jsonObject.getDouble("imc")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao carregar o histórico do JSON: ${e.message}")
        }
    }

    // (saveHistoryToJson - sem alteração)
    private fun saveHistoryToJson() {
        try {
            val jsonArray = JSONArray()
            for (record in historyList) {
                val jsonObject = JSONObject()
                jsonObject.put("nome", record.nome)
                jsonObject.put("data", record.data)
                jsonObject.put("hora", record.hora)
                jsonObject.put("peso", record.peso)
                jsonObject.put("altura", record.altura)
                jsonObject.put("imc", record.imc)
                jsonArray.put(jsonObject)
            }
            val file = File(filesDir, JSON_FILE_NAME)
            file.writeText(jsonArray.toString(4))
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar o histórico no JSON: ${e.message}")
        }
    }

    // (deleteHistoryJsonFile - sem alteração)
    private fun deleteHistoryJsonFile() {
        try {
            val file = File(filesDir, JSON_FILE_NAME)
            if (file.exists()) {
                file.delete()
                Log.i(TAG, "Ficheiro de histórico apagado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao apagar o ficheiro de histórico: ${e.message}")
        }
    }

    // (updateHistoryTable - sem alteração)
    private fun updateHistoryTable() {
        if (historyTableContent.childCount > 1) {
            historyTableContent.removeViews(1, historyTableContent.childCount - 1)
        }

        if (historyList.isEmpty()) {
            val emptyRow = TableRow(this)
            val emptyCell = TextView(this)
            emptyCell.text = "- Sem medições -"
            emptyCell.setTextColor(resources.getColor(android.R.color.white, null))
            emptyCell.gravity = Gravity.CENTER
            val padding = (16 * resources.displayMetrics.density).toInt()
            emptyCell.setPadding(padding, padding, padding, padding)
            val params = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            params.span = 6
            emptyCell.layoutParams = params
            emptyRow.addView(emptyCell)
            historyTableContent.addView(emptyRow)
            imcHistoryLayout.visibility = View.VISIBLE
            return
        }

        val last5Records = historyList.takeLast(5).reversed()
        for (record in last5Records) {
            val tableRow = TableRow(this)
            tableRow.addView(createHistoryCell(record.nome))
            tableRow.addView(createHistoryCell(record.data))
            tableRow.addView(createHistoryCell(record.hora))
            tableRow.addView(createHistoryCell(record.peso.toString()))
            tableRow.addView(createHistoryCell(record.altura.toString()))
            tableRow.addView(createHistoryCell("%.2f".format(record.imc)))
            historyTableContent.addView(tableRow)
        }
        imcHistoryLayout.visibility = View.VISIBLE
    }

    // (createHistoryCell - sem alteração)
    private fun createHistoryCell(text: String): TextView {
        val textView = TextView(this)
        textView.text = text
        textView.setTextColor(resources.getColor(android.R.color.white, null))
        val padding = (4 * resources.displayMetrics.density).toInt()
        textView.setPadding(padding, padding, padding, padding)
        return textView
    }

    // (getCurrentDateTime - sem alteração)
    private fun getCurrentDateTime(): Pair<String, String> {
        val now = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return Pair(dateFormat.format(now), timeFormat.format(now))
    }

    // --- FUNÇÃO exportHistoryToJson (COM UMA PEQUENA MELHORIA) ---
    private fun exportHistoryToJson() {
        saveHistoryToJson()
        val file = File(filesDir, JSON_FILE_NAME)
        if (!file.exists() || historyList.isEmpty()) {
            Toast.makeText(this, "Não há histórico para partilhar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "application/json"
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri)

            // --- PEQUENA MELHORIA ---
            // Adiciona um "Assunto", útil para apps como o Gmail
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Histórico de IMC (JSON)")
            // --- Fim da Melhoria ---

            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(shareIntent, "Partilhar histórico..."))

        } catch (e: Exception) {
            Log.e(TAG, "Falha ao partilhar o ficheiro JSON: ${e.message}")
            Toast.makeText(this, "Não foi possível partilhar o ficheiro.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // (HeightTextWatcher - sem alteração)
    private inner class HeightTextWatcher(private val editText: TextInputEditText) : TextWatcher {
        private var isUpdating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating) return
            isUpdating = true
            val originalText = s.toString()
            var digitsOnly = originalText.filter { it.isDigit() }
            var formattedText = ""
            var newCursorPos = 0
            if (digitsOnly.isNotEmpty()) {
                if (digitsOnly[0] != '0' && digitsOnly[0] != '1') {
                    digitsOnly = digitsOnly.dropLast(1)
                    newCursorPos = 0
                } else if (digitsOnly.length == 1) {
                    formattedText = digitsOnly
                    newCursorPos = 1
                } else {
                    if (digitsOnly.length > 3) {
                        digitsOnly = digitsOnly.substring(0, 3)
                    }
                    formattedText = digitsOnly.substring(0, 1) + "." + digitsOnly.substring(1)
                    newCursorPos = formattedText.length
                }
            }
            editText.setText(formattedText)
            editText.setSelection(newCursorPos.coerceAtMost(formattedText.length))
            isUpdating = false
        }
    }

} //fim da MainActivity