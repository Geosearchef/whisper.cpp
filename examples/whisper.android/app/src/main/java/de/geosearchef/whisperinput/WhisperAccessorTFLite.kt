package de.geosearchef.whisperinput

import android.app.Application
import com.whispertflite.TFWhisperEngine
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture

class WhisperAccessorTFLite : WhisperAccessor() {

    var engine: TFWhisperEngine? = null

    override fun transcribe(language: String, translate: Boolean): CompletableFuture<String> = CompletableFuture.supplyAsync {
        val engine = engine ?: throw RuntimeException("Model not loaded")
        val lastRecording = lastRecording ?: throw RuntimeException("Last recording file not found")

        val transcription = engine.transcribeFile(lastRecording)
        return@supplyAsync transcription
    }

    override fun loadModelAsync(application: Application, model: WhisperAccessor.Model): CompletableFuture<Void> = CompletableFuture.runAsync {
        engine?.let { it.release() }

        val modelFileName = model.tfModelFileName ?: throw RuntimeException("No TF model found for this model")
        val vocabFileName = model.tfVocabFileName ?: throw RuntimeException("No TF vocab found for this model")

        engine = TFWhisperEngine().apply {
            initialize(application.assets.openFd("models/" + modelFileName), application.assets.open("models/" + vocabFileName), true)
            check(isInitialized)
        }
    }

    override fun isModelAvailable(application: Application, model: WhisperAccessor.Model): Boolean {
        return model.tfModelFileName != null
                && model.tfVocabFileName != null
                && application.assets.list("models/")?.let { model.tfModelFileName in it && model.tfVocabFileName in it } ?: false
    }

}