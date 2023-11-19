package de.geosearchef.whisperinput

import android.app.Application
import com.whispercpp.whisper.WhisperContext
import com.whispercppdemo.media.decodeWaveFile
import com.whispercppdemo.recorder.Recorder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture

class WhisperAccessorCpp : WhisperAccessor() {

    var whisperContext: WhisperContext? = null

    override fun transcribe(language: String, translate: Boolean) : CompletableFuture<String> = CompletableFuture.supplyAsync {
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

    override fun loadModelAsync(application: Application, model: WhisperAccessor.Model) = CompletableFuture.runAsync {
        whisperContext?.let { runBlocking { it.release() } }
        whisperContext = WhisperContext.createContextFromAsset(application.assets, "models/" + model.fileName)
    }

    override fun isModelAvailable(application: Application, model: WhisperAccessor.Model): Boolean {
        return application.assets.list("models/")?.let { model.fileName in it } ?: false
    }
}