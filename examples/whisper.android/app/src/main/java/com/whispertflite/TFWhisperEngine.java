package com.whispertflite;

import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;


public class TFWhisperEngine {
    private final String TAG = "TFWhispher";
    private final TFWhisperUtil mWhisperUtil = new TFWhisperUtil();

    private boolean mIsInitialized = false;
    private Interpreter mInterpreter = null;

    public boolean isInitialized() {
        return mIsInitialized;
    }


    public boolean initialize(AssetFileDescriptor modelFileDescriptor, InputStream vocabStream, boolean multilingual) throws IOException {
        // Load model
        loadModel(modelFileDescriptor);
        Log.i(TAG, "Model is loaded...");

        // Load filters and vocab
        boolean ret = mWhisperUtil.loadFiltersAndVocab(multilingual, vocabStream);
        if (ret) {
            mIsInitialized = true;
            Log.d(TAG, "Filters and Vocab are loaded...");
        } else {
            mIsInitialized = false;
            Log.e(TAG, "Failed to load Filters and Vocab...");
        }

        return mIsInitialized;
    }

    public String transcribeFile(File audioFile) {
        // Calculate Mel spectrogram
        Log.d(TAG, "Calculating Mel spectrogram...");
        float[] melSpectrogram = getMelSpectrogram(audioFile);
        Log.d(TAG, "Mel spectrogram is calculated...!");

        // Perform inference
        String result = runInference(melSpectrogram);
        Log.d(TAG, "Inference is executed...!");

        return result;
    }

    // Load TFLite model
    private void loadModel(AssetFileDescriptor modelFileDescriptor) throws IOException {
//        ByteBuffer tfliteModel = ByteBuffer.wrap(Files.readAllBytes(modelFile.toPath()));
//        ByteBuffer tfliteModel = null;
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { // TODO: this forces Android 13 for this feature
//            byte[] modelData = modelStream.readAllBytes();
//            tfliteModel = ByteBuffer.allocateDirect(modelData.length);
//            tfliteModel.order(ByteOrder.nativeOrder());
//            tfliteModel.put(modelData);
//
//            // TODO: data is duplicated here, -> read into direct buffer directly? alternative: use a mappedByteBuffer from the asset file
//        } else {
//            throw new RuntimeException("Your android version is outdated");
//        }


        FileInputStream is = new FileInputStream(modelFileDescriptor.getFileDescriptor());
        FileChannel channel = is.getChannel();
        long startOffset = modelFileDescriptor.getStartOffset();
        long declaredLength = modelFileDescriptor.getDeclaredLength();
        MappedByteBuffer tfliteModel = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

        // Set the number of threads for inference
        Interpreter.Options options = new Interpreter.Options();
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            NnApiDelegate nnApiDelegate = new NnApiDelegate();
//            options.addDelegate(nnApiDelegate);
//        } else {
//            options.setNumThreads(Runtime.getRuntime().availableProcessors());
//        }

        CompatibilityList compatList = new CompatibilityList();
        if(compatList.isDelegateSupportedOnThisDevice()) {
            Log.i(TAG, "loadModel: GPU SUPPORT ENABLED");
            options.addDelegate(new GpuDelegate(compatList.getBestOptionsForThisDevice()));
        } else {
            Log.i(TAG, "loadModel: GPU SUPPORT UNAVAILABLE");
            options.setNumThreads(Runtime.getRuntime().availableProcessors());
        }

        // crashes again on load. NNAPI? loader above? debug -> debugger
        // TPU?
        // conversion notebook: larger models? if only second cell executes, notebook works for generate model

        mInterpreter = new Interpreter(tfliteModel, options);
    }

    private float[] getMelSpectrogram(File audioFile) {
        // Get samples in PCM_FLOAT format
        float[] samples = TFWaveUtil.getSamples(audioFile);

        int fixedInputSize = TFWhisperUtil.WHISPER_SAMPLE_RATE * TFWhisperUtil.WHISPER_CHUNK_SIZE;
        float[] inputSamples = new float[fixedInputSize];
        int copyLength = Math.min(samples.length, fixedInputSize);
        System.arraycopy(samples, 0, inputSamples, 0, copyLength);

        int cores = Runtime.getRuntime().availableProcessors();
        return mWhisperUtil.getMelSpectrogram(inputSamples, inputSamples.length, cores);
    }

    private String runInference(float[] inputData) {
        // Create input tensor
        Tensor inputTensor = mInterpreter.getInputTensor(0);
        TensorBuffer inputBuffer = TensorBuffer.createFixedSize(inputTensor.shape(), inputTensor.dataType());
        Log.d(TAG, "Input Tensor Dump ===>");
        printTensorDump(inputTensor);

        // Create output tensor
        Tensor outputTensor = mInterpreter.getOutputTensor(0);
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32);
        Log.d(TAG, "Output Tensor Dump ===>");
        printTensorDump(outputTensor);

        // Load input data
        int inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.BYTES;
        ByteBuffer inputBuf = ByteBuffer.allocateDirect(inputSize);
        inputBuf.order(ByteOrder.nativeOrder());
        for (float input : inputData) {
            inputBuf.putFloat(input);
        }

        // To test mel data as a input directly
//        try {
//            byte[] bytes = Files.readAllBytes(Paths.get("/data/user/0/com.example.tfliteaudio/files/mel_spectrogram.bin"));
//            inputBuf = ByteBuffer.wrap(bytes);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        inputBuffer.loadBuffer(inputBuf);

        // Run inference
        mInterpreter.run(inputBuffer.getBuffer(), outputBuffer.getBuffer());

        // Retrieve the results
        int outputLen = outputBuffer.getIntArray().length;
        Log.d(TAG, "output_len: " + outputLen);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < outputLen; i++) {
            int token = outputBuffer.getBuffer().getInt();
            if (token == mWhisperUtil.getTokenEOT())
                break;

            // Get word for token and Skip additional token
            if (token < mWhisperUtil.getTokenEOT()) {
                String word = mWhisperUtil.getWordFromToken(token);
                Log.d(TAG, "Adding token: " + token + ", word: " + word);
                result.append(word);
            } else {
                if (token == mWhisperUtil.getTokenTranscribe())
                    Log.d(TAG, "It is Transcription...");

                if (token == mWhisperUtil.getTokenTranslate())
                    Log.d(TAG, "It is Translation...");

                String word = mWhisperUtil.getWordFromToken(token);
                Log.d(TAG, "Skipping token: " + token + ", word: " + word);
            }
        }

        return result.toString();
    }

    public void release() {
        mInterpreter.close();
    }

    private void printTensorDump(Tensor tensor) {
        Log.d(TAG, "  shape.length: " + tensor.shape().length);
        for (int i = 0; i < tensor.shape().length; i++)
            Log.d(TAG, "    shape[" + i + "]: " + tensor.shape()[i]);
        Log.d(TAG, "  dataType: " + tensor.dataType());
        Log.d(TAG, "  name: " + tensor.name());
        Log.d(TAG, "  numBytes: " + tensor.numBytes());
        Log.d(TAG, "  index: " + tensor.index());
        Log.d(TAG, "  numDimensions: " + tensor.numDimensions());
        Log.d(TAG, "  numElements: " + tensor.numElements());
        Log.d(TAG, "  shapeSignature.length: " + tensor.shapeSignature().length);
        Log.d(TAG, "  quantizationParams.getScale: " + tensor.quantizationParams().getScale());
        Log.d(TAG, "  quantizationParams.getZeroPoint: " + tensor.quantizationParams().getZeroPoint());
        Log.d(TAG, "==================================================================");
    }
}
