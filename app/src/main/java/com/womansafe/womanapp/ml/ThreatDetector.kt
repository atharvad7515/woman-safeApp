package com.womansafe.womanapp.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ThreatDetector(context: Context) {
    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFile(context, "cnn_model_balanced.tflite")
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Predict if the current sensor data indicates a real threat.
     * Input: Array of features (the shape must match your model's input)
     * Output: Probability (0.0 to 1.0)
     */
    fun predict(inputData: FloatArray): Float {
        if (interpreter == null) return 0f
        
        // Reshape input to [1, input_size] if necessary for your model
        val output = Array(1) { FloatArray(1) }
        interpreter?.run(arrayOf(inputData), output)
        return output[0][0]
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
