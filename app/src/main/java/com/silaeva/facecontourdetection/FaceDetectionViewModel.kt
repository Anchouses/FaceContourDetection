package com.silaeva.facecontourdetection

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.TaskExecutors
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow

class FaceDetectionViewModel : ViewModel() {

    private val exceptionTag = "EXCEPTION"
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    var listFaces = MutableStateFlow<List<Face>>(emptyList())

    fun startCamera(
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner,
        setSourceInfo: (SourceInfo) -> Unit,
        cameraSelector: CameraSelector,
        imageCapture: ImageCapture
    ) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
        cameraProviderFuture?.addListener(
            {
                val cameraProvider = cameraProviderFuture?.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                var sourceInfoUpdated = false

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(
                            ContextCompat.getMainExecutor(previewView.context)
                        ) { imageProxy ->
                            if (!sourceInfoUpdated) {
                                setSourceInfo(obtainSourceInfo(cameraSelector, imageProxy))
                                sourceInfoUpdated = true
                            }
                            try {
                                processImage(
                                    imageProxy,
                                    getListFaces = { listFaces.value = it }
                                )
                            } catch (e: MlKitException) {
                                Log.e(
                                    "CAMERA",
                                    "Failed to process image. Error: " + e.localizedMessage
                                )
                            }
                        }
                    }

                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e(exceptionTag, "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(previewView.context)
        )
    }

    private fun obtainSourceInfo(
        cameraSelector: CameraSelector,
        imageProxy: ImageProxy
    ): SourceInfo {
        val isImageFlipped = cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees == 0 || rotationDegrees == 180) {
            SourceInfo(
                height = imageProxy.height,
                width = imageProxy.width,
                isImageFlipped = isImageFlipped
            )
        } else {
            SourceInfo(
                height = imageProxy.width,
                width = imageProxy.height,
                isImageFlipped = isImageFlipped
            )
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy, getListFaces: (MutableList<Face>) -> Unit) {
        val executor = TaskExecutors.MAIN_THREAD

        val mediaImage = imageProxy.image ?: return

        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.4f)
            .build()

        val detector = FaceDetection.getClient(options)

        detector.process(inputImage)
            .addOnSuccessListener(executor) { faces: List<Face> ->
                getListFaces(faces.toMutableList())
            }
            .addOnFailureListener(executor) { e ->
                Log.e(exceptionTag, "Binding failed", e)
                imageProxy.close()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
