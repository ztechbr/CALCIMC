package io.ztech.imcapp

// IMPORTAÇÕES ADICIONADAS
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast // Importação para o aviso
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import android.util.Log // <-- 1. IMPORTE A CLASSE LOG

class MainActivity : AppCompatActivity() {

    // --- 2. ADICIONE ESTE BLOCO 'companion object' AQUI ---
    companion object {
        // Você pode usar qualquer nome para a TAG, mas o nome da classe é uma boa prática
        private const val TAG = "MainActivity"
    }

    private lateinit var etPeso: TextInputEditText
    private lateinit var etAltura: TextInputEditText
    private lateinit var tvResultado: TextView
    private lateinit var btCalcular: Button
    private lateinit var btLimpar: Button

    // Declara o ToneGenerator
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Inicializa o ToneGenerator
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100) // 100 = Volume
        } catch (e: Exception) {
            e.printStackTrace()
            // Lidar com o erro se o áudio não estiver disponível
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        init()

        btCalcular.setOnClickListener {
            btCalcularOnClick()
        }

        // --- MUDANÇA AQUI ---

        // 1. O clique normal agora avisa o usuário
        btLimpar.setOnClickListener {
            Toast.makeText(this, "Segure para limpar", Toast.LENGTH_SHORT).show()
        }

        // 2. O Long Click (clique longo) é que faz a ação
        btLimpar.setOnLongClickListener {
            // Chama sua função de limpeza
            btLimparOnClick()

            // Toca o "beep"
            playBeep()

            // Retorna 'true' para indicar que o evento foi consumido
            // (e não deve acionar o clique normal depois)
            true
        }
    } // fim do onCreate()

    override fun onDestroy() {
        super.onDestroy()
        // Libera o recurso do ToneGenerator quando a activity for destruída
        toneGenerator?.release()
    }

    private fun init() {
        etPeso = findViewById(R.id.etPeso)
        etAltura = findViewById(R.id.etAltura)
        tvResultado = findViewById(R.id.tvResultado)
        btCalcular = findViewById(R.id.btCalcular)
        btLimpar = findViewById(R.id.btLimpar)
    }

    private fun btCalcularOnClick() {
        etPeso.error = null
        etAltura.error = null

        val peso = etPeso.text.toString().toDoubleOrNull()
        val altura = etAltura.text.toString().toDoubleOrNull()

        if (peso == null) {
            etPeso.error = "Peso deve ser informado."
            return
        }
        if (altura == null) {
            etAltura.error = "Altura deve ser informada."
            return
        }
        if (altura == 0.0) {
            etAltura.error = "Altura não pode ser zero."
            return
        }

        val resultado = peso / (altura * altura)
        tvResultado.text = "%.2f".format(resultado)
    }

    private fun btLimparOnClick() {
        etPeso.text?.clear()
        etAltura.text?.clear()
        tvResultado.text = "-"
        etPeso.error = null
        etAltura.error = null
        etPeso.requestFocus()
    }

    // --- NOVA FUNÇÃO ---
    /**
     * Toca um som curto de "beep".
     */
    private fun playBeep() {
        try {
            // TONE_CDMA_PIP é um som de "beep" curto e comum
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 150) // 150ms de duração
            println("Deu beep")
        } catch (e: Exception) {
            e.printStackTrace()
            // 3. USE O LOG.E COM A TAG E A MENSAGEM DE ERRO
            Log.e(TAG, "Erro ao tocar o beep: ${e.message}")
        }
    }

} //fim da MainActivity