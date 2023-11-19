package de.geosearchef.whisperinput

import android.app.Application
import com.whispercppdemo.recorder.Recorder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CompletableFuture

abstract class WhisperAccessor {

    var lastRecording: File? = null
    val recorder = Recorder()

    fun startRecording(): Unit = runBlocking {
        generateTmpRecordingFile().let { file ->
            lastRecording = file
            recorder.startRecording(file, Exception::printStackTrace)
        }
    }

    fun stopRecording() = runBlocking {
        recorder.stopRecording()
    }

    fun generateTmpRecordingFile() = File.createTempFile("rec", "wav")


    abstract fun transcribe(language: String = "auto", translate: Boolean = false) : CompletableFuture<String>
    abstract fun loadModelAsync(application: Application, model: WhisperAccessor.Model = WhisperAccessor.Model.DEFAULT): CompletableFuture<Void>
    abstract fun isModelAvailable(application: Application, model: Model): Boolean

    enum class Model(val fileName: String, val tfModelFileName: String? = null, val tfVocabFileName: String? = null, val label: String = fileName) {
        TINY("ggml-tiny.bin", "whisper-tiny.tflite", "tflt-vocab-mel-tiny.bin"), BASE("ggml-base.bin"), SMALL_Q("ggml-small-q5_1.bin", "whisper-small.tflite", "tflt-vocab-mel-small.bin"),
        MEDIUM_Q("ggml-medium-q5_0.bin"),
        TINY_EN("ggml-tiny.en.bin"), BASE_EN("ggml-base.en.bin"), SMALL_EN_Q("ggml-small.en-q5_1.bin");

        companion object {
            val DEFAULT = BASE
        }
    }
}