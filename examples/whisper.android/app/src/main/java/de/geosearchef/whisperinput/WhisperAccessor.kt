package de.geosearchef.whisperinput

import android.app.Application
import android.widget.Toast
import com.whispercpp.whisper.WhisperContext
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.RuntimeException
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

object WhisperAccessor {

    var lastRecording: File? = null
    val recorder = Recorder()

    var whisperContext: WhisperContext? = null

    fun startRecording() = runBlocking {
        generateTmpRecordingFile().let { file ->
            lastRecording = file
            recorder.startRecording(file, Exception::printStackTrace)
        }
    }

    fun stopRecording() = runBlocking {
        recorder.stopRecording()
    }

    fun transcribe(language: String = "auto", translate: Boolean = false) : CompletableFuture<String> = CompletableFuture.supplyAsync {
        val context = whisperContext ?: throw RuntimeException("Model not loaded")

        val audio = lastRecording?.let { decodeWaveFile(it) } ?: throw RuntimeException("Last recording file not found")

        // TODO: do we have to delete the audio again?
        // TODO: do not write to disk

        // TODO: add cleanup, e.g. context.release()
        return@supplyAsync runBlocking {
            val transcription = context.transcribeData(audio, language = language, translate = translate)
            return@runBlocking transcription
        }
    }

    fun loadModelAsync(application: Application, model: Model = Model.DEFAULT) = CompletableFuture.runAsync {
        whisperContext?.let { runBlocking { it.release() } }
        whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/" + model.fileName)
    }

    fun generateTmpRecordingFile() = File.createTempFile("rec", "wav")

    enum class Model(val fileName: String, val label: String = fileName) {
        TINY("ggml-tiny.bin"), BASE("ggml-base.bin"), SMALL_Q("ggml-small-q5_1.bin"),
        MEDIUM_Q("ggml-medium-q5_0.bin"),
        TINY_EN("ggml-tiny.en.bin"), BASE_EN("ggml-base.en.bin"), SMALL_EN_Q("ggml-small.en-q5_1.bin");

        companion object {
            val DEFAULT = BASE
        }
    }
}