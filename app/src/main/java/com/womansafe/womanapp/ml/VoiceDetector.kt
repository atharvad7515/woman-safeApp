package com.womansafe.womanapp.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

class VoiceDetector(context: Context) {
    private var interpreter: Interpreter? = null
    private var inputShape: IntArray? = null
    var inputSize: Int = 16000 
        private set

    init {
        try {
            val model = loadModelFile(context, "cnn_model_balanced.tflite")
            interpreter = Interpreter(model)
            inputShape = interpreter?.getInputTensor(0)?.shape()
            Log.d("VoiceDetector", "2D CNN Loaded. Shape: ${inputShape?.contentToString()}")
        } catch (e: Exception) {
            Log.e("VoiceDetector", "Error loading model", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun predict(audioData: FloatArray): Float {
        if (interpreter == null) return 1.0f
        
        // 1. PEAK NORMALIZATION (Boosts whispers to standard level)
        val normalizedAudio = FloatArray(audioData.size)
        var maxVal = 0.001f
        for (x in audioData) if (abs(x) > maxVal) maxVal = abs(x)
        for (i in audioData.indices) normalizedAudio[i] = audioData[i] / maxVal
        
        // 2. Transform to 2D Mel-Spectrogram
        val modelInput = transformToMel2D(normalizedAudio)
        
        val output = Array(1) { FloatArray(1) }
        try {
            interpreter?.run(modelInput, output)
            return output[0][0] 
        } catch (e: Exception) {
            Log.e("VoiceDetector", "Prediction error", e)
            return 1.0f
        }
    }

    private fun transformToMel2D(audioData: FloatArray): Array<Array<Array<FloatArray>>> {
        val numFrames = 20
        val numBins = 32
        val frameSize = audioData.size / numFrames 
        val spectrogram = Array(1) { Array(numFrames) { Array(numBins) { FloatArray(1) } } }

        // Precompute Mel-spaced frequency indices
        val melIndices = FloatArray(numBins)
        val melMax = 2595.0 * log10(1.0 + 8000.0 / 700.0)
        for (i in 0 until numBins) {
            val mel = i.toDouble() * (melMax / (numBins - 1))
            val freq = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
            melIndices[i] = (freq / (16000.0 / frameSize)).toFloat()
        }

        for (f in 0 until numFrames) {
            val frame = FloatArray(frameSize)
            System.arraycopy(audioData, f * frameSize, frame, 0, frameSize)
            
            // Hanning Window
            for (n in 0 until frameSize) {
                frame[n] *= (0.5f * (1 - cos(2.0 * PI * n / (frameSize - 1)))).toFloat()
            }

            for (k in 0 until numBins) {
                var real = 0f
                var imag = 0f
                val centerIdx = melIndices[k]
                
                for (n in 0 until frameSize) {
                    val angle = 2.0 * PI * centerIdx * n / frameSize
                    real += frame[n] * cos(angle).toFloat()
                    imag -= frame[n] * sin(angle).toFloat()
                }
                
                val mag = sqrt((real * real + imag * imag).toDouble()).toFloat()
                spectrogram[0][f][k][0] = log10(mag.toDouble() + 1e-6).toFloat()
            }
        }
        return spectrogram
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
