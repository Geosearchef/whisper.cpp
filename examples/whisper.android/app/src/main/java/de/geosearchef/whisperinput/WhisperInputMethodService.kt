package de.geosearchef.whisperinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.whispercppdemo.R
import com.whispercppdemo.recorder.Recorder
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WhisperInputMethodService : InputMethodService() {

    lateinit var recordButton: ToggleButton
    lateinit var computationIndicator: ProgressBar
    lateinit var modelLoadingIndicator: ProgressBar

    var isRecording: Boolean = false

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreateInputView(): View {
//        applicationContext.setTheme(R.style.Theme_WhisperInput)
        val inflater = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_WhisperInput))
        val view = inflater.inflate(R.layout.input_method_layout, null)

        recordButton = view.findViewById(R.id.recordButton)
        computationIndicator = view.findViewById(R.id.computationIndicator)
        modelLoadingIndicator = view.findViewById(R.id.modelLoadingIndicator)

        recordButton.setOnCheckedChangeListener { buttonView, isChecked ->
            if(isChecked) {
                if(requestPermission()) {
                    isRecording = true
                    WhisperAccessor.startRecording()
                } else {
                    recordButton.isChecked = false
                }
            } else if(isRecording) {
                // stop recording + execute
                isRecording = false
                WhisperAccessor.stopRecording()

                // TODO: can this fall through the cracks? wrong state? time out?

                // transcribe
                transcribe()
            }
        }

        // load default model
//        loadModel()
        loadModel(WhisperAccessor.Model.BASE)

        return view
    }

    fun loadModel(model: WhisperAccessor.Model = WhisperAccessor.Model.DEFAULT) {
        // Load default model
        modelLoadingIndicator.visibility = VISIBLE
        WhisperAccessor.loadModelAsync(application, model).thenRun {
            modelLoadingIndicator.visibility = INVISIBLE
            println("Model loaded")
        }
    }

    fun transcribe() {
        computationIndicator.visibility = VISIBLE
        WhisperAccessor.transcribe().thenAccept { transcription ->
            computationIndicator.visibility = INVISIBLE
            println(transcription)

            currentInputConnection.commitText(transcription.trim(), 1)
        }
    }


    fun requestPermission(): Boolean {
        val permissionGranted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if(!permissionGranted) {
//            ActivityCompat.requestPermissions()   // requires activity
            Toast.makeText(applicationContext, "Permission to record Audio not granted.", Toast.LENGTH_LONG).show()
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).let {
                it.data = Uri.fromParts("package", packageName, null)
                startActivity(it)
            }
        }

        return permissionGranted
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        // https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method#kotlin
        super.onStartInputView(editorInfo, restarting)
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}