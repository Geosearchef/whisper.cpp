package de.geosearchef.whisperinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.whispercppdemo.R

class WhisperInputMethodService : InputMethodService() {

    lateinit var recordButton: ToggleButton
    lateinit var improveButton: Button

    lateinit var computationIndicator: ProgressBar
    lateinit var modelLoadingIndicator: ProgressBar

    lateinit var modelSelectorGroup: RadioGroup
    lateinit var languageSelectorGroup: RadioGroup
    lateinit var modelSelectorAccurate: RadioButton
    lateinit var modelSelectorBalanced: RadioButton
    lateinit var modelSelectorFast: RadioButton
    lateinit var languageSelectorAuto: RadioButton
    lateinit var languageSelectorEnglish: RadioButton
    lateinit var languageSelectorGerman: RadioButton
    lateinit var languageSelectorEnglModel: RadioButton

    lateinit var spaceButton: ImageButton
    lateinit var deleteButton: ImageButton
    lateinit var returnButton: ImageButton

    lateinit var swapKeyboardButton: ImageButton
    lateinit var exitButton: ImageButton

    lateinit var translateSwitch: Switch

    var isRecording: Boolean = false
    var isImprovePending: Boolean = false
    var lastInsert: String = ""

    override fun onCreateInputView(): View {
//        applicationContext.setTheme(R.style.Theme_WhisperInput)
        val inflater = LayoutInflater.from(ContextThemeWrapper(this, R.style.Theme_WhisperInput))
        val view = inflater.inflate(R.layout.input_method_layout, null)

        recordButton = view.findViewById(R.id.recordButton)
        improveButton = view.findViewById(R.id.improveButton)

        computationIndicator = view.findViewById(R.id.computationIndicator)
        modelLoadingIndicator = view.findViewById(R.id.modelLoadingIndicator)

        modelSelectorGroup = view.findViewById(R.id.modelSelectorGroup)
        languageSelectorGroup = view.findViewById(R.id.languageSelectorGroup)

        modelSelectorAccurate = view.findViewById(R.id.modelSelectorAccurate)
        modelSelectorBalanced = view.findViewById(R.id.modelSelectorBalanced)
        modelSelectorFast = view.findViewById(R.id.modelSelectorFast)

        languageSelectorAuto = view.findViewById(R.id.languageSelectorAuto)
        languageSelectorEnglish = view.findViewById(R.id.languageSelectorEnglish)
        languageSelectorGerman = view.findViewById(R.id.languageSelectorGerman)
        languageSelectorEnglModel = view.findViewById(R.id.languageSelectorEnglishModel)

        spaceButton = view.findViewById(R.id.spaceButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        returnButton = view.findViewById(R.id.returnButton)

        swapKeyboardButton = view.findViewById(R.id.defaultInputMethodSwitchButton)
        exitButton = view.findViewById(R.id.exitButton)

        translateSwitch = view.findViewById(R.id.translateSwitch)

        recordButton.setOnCheckedChangeListener { buttonView, isChecked ->
            if(isChecked) {
                if(requestPermission()) {
                    improveButton.isEnabled = false
                    modelSelectorGroup.children.forEach { it.isEnabled = false }
                    languageSelectorGroup.children.forEach { it.isEnabled = false }

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

        improveButton.setOnClickListener {
            if(currentInputConnection.getTextBeforeCursor(lastInsert.length, 0) == lastInsert) {
                currentInputConnection.deleteSurroundingText(lastInsert.length, 0)
            }

            isImprovePending = true
            getImprovedModelButton(view.findViewById(modelSelectorGroup.checkedRadioButtonId) as Button).isChecked = true
        }

        // TODO: lifecycle mgmt on the IME

        modelSelectorGroup.setOnCheckedChangeListener { group, selectedId ->
//            loadModel(getSelectedModel(selectedId))
            loadSelectedModel()
        }

        spaceButton.setOnClickListener {
            currentInputConnection.commitText(" ", 1)
        }

        deleteButton.setOnClickListener {
            currentInputConnection.getTextBeforeCursor(20, 0)?.let { textBeforeCursor ->
//                var textEncountered = false
//                var charactersToDelete = 0
//                for(i in textBeforeCursor.indices.reversed()) {
//                    if(textBeforeCursor[i] != ' ') {
//                        textEncountered = true
//                    } else if(textEncountered) {
//                        break
//                    }
//                    charactersToDelete++
//                }
                var charactersToDelete = 1
                for(i in (0 until textBeforeCursor.length - 1).reversed()) {
                    if(textBeforeCursor[i].isWhitespace()) {
                        break
                    }
                    charactersToDelete++
                }

                currentInputConnection.deleteSurroundingText(charactersToDelete, 0)
            }
        }

        returnButton.setOnClickListener {
            currentInputConnection.commitText("\n", 1)
        }

        swapKeyboardButton.setOnClickListener {
//            switchInputMethod("asd")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            } else {
                println("Android version to old to switch to previous input method.")
            }
        }

        exitButton.setOnClickListener {
//            switchInputMethod("asd")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                println("Android version to old to switch to previous input method.")
            }
        }

        // load default model
//        loadModel()
        loadModel(WhisperAccessor.Model.BASE)

        return view
    }

    private fun getSelectedModel(selectedId: Int) = when (selectedId) {
        R.id.modelSelectorAccurate -> WhisperAccessor.Model.SMALL_Q
        R.id.modelSelectorBalanced -> WhisperAccessor.Model.BASE
        R.id.modelSelectorFast -> WhisperAccessor.Model.TINY
        else -> WhisperAccessor.Model.BASE
    }

    private fun getImprovedModelButton(current: Button) = when(current) {
        modelSelectorAccurate -> modelSelectorAccurate
        modelSelectorBalanced -> modelSelectorAccurate
        modelSelectorFast -> modelSelectorBalanced
        else -> modelSelectorAccurate
    }

    fun loadSelectedModel() {
        loadModel(getSelectedModel(modelSelectorGroup.checkedRadioButtonId))
    }

    fun loadModel(model: WhisperAccessor.Model = WhisperAccessor.Model.DEFAULT) {
        recordButton.isEnabled = false
        improveButton.isEnabled = false
        modelSelectorGroup.children.forEach { it.isEnabled = false }
        languageSelectorGroup.children.forEach { it.isEnabled = false }
        modelLoadingIndicator.visibility = VISIBLE

        WhisperAccessor.loadModelAsync(application, model).thenRun {
            println("Model loaded")
            Handler(Looper.getMainLooper()).post {
                modelLoadingIndicator.visibility = INVISIBLE
                recordButton.isEnabled = true
//                improveButton.isEnabled = true
                modelSelectorGroup.children.forEach { it.isEnabled = true }
                languageSelectorGroup.children.forEach { it.isEnabled = true }

                if(isImprovePending) {
                    isImprovePending = false
                    transcribe()
                }
            }
        }
    }

    fun transcribe() {
        recordButton.isEnabled = false
        improveButton.isEnabled = false
        modelSelectorGroup.children.forEach { it.isEnabled = false }
        languageSelectorGroup.children.forEach { it.isEnabled = false }

        val language = when(languageSelectorGroup.checkedRadioButtonId) {
            R.id.languageSelectorAuto -> "auto"
            R.id.languageSelectorEnglish -> "en"
            R.id.languageSelectorGerman -> "de"
            R.id.languageSelectorEnglishModel -> "en"
            else -> "auto"
        }
        val translate = translateSwitch.isChecked

        println("Using language $language, translate=$translate")

        computationIndicator.visibility = VISIBLE
        WhisperAccessor.transcribe(language, translate).thenAccept { transcription ->
            println(transcription)

            currentInputConnection.commitText(transcription.trim(), 1)
            lastInsert = transcription.trim()

            Handler(Looper.getMainLooper()).post {
                computationIndicator.visibility = INVISIBLE
                recordButton.isEnabled = true
                improveButton.isEnabled = true
                modelSelectorGroup.children.forEach { it.isEnabled = true }
                languageSelectorGroup.children.forEach { it.isEnabled = true }
            }
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