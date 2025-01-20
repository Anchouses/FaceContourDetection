package com.silaeva.facecontourdetection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.Face
import java.io.File


@Composable
fun CameraPreview(
    viewModel: FaceDetectionViewModel,
    onCapture: (Bitmap, File) -> Unit,
    cameraSelector: CameraSelector,
    onSwitchCamera:(CameraSelector) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val sourceInfo = remember { mutableStateOf(SourceInfo(10, 10, false)) }
    val listFaces = viewModel.listFaces.collectAsState()
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(cameraSelector) {
        viewModel.startCamera(
            previewView,
            lifecycleOwner,
            setSourceInfo = { sourceInfo.value = it },
            cameraSelector,
            imageCapture
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        with(LocalDensity.current) {
            Box(
                modifier = Modifier
                    .size(
                        height = sourceInfo.value.height.toDp(),
                        width = sourceInfo.value.width.toDp()
                    )
                    .scale(
                        calculateScale(
                            constraints,
                            sourceInfo.value,
                            PreviewScaleType.CENTER_CROP
                        )
                    )
            )
            {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        previewView.apply {
                            this.scaleType = PreviewView.ScaleType.FIT_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        previewView
                    }
                )

                DetectedFaces(
                    faces = listFaces.value,
                    sourceInfo = sourceInfo.value
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(bottom = 60.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SwitchButton(
                onSwitchCamera = {
                    onSwitchCamera(cameraSelector)
                }
            )

            Button(
                onClick = {
                    val photoFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${System.currentTimeMillis()}.jpg")
                    Log.d("PhotoPath", photoFile.absolutePath)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                onCapture(bitmap, photoFile)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("EXCEPTION", "ImageCaptureException", exception)
                            }
                        }
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray
                )
            ) {
                Text(
                    text = "Capture",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SwitchButton(
    onSwitchCamera: () -> Unit
) {
    Button(
        onClick = { onSwitchCamera() },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.DarkGray
        )
    ) {
        Text(
            text = "Switch camera",
            color = Color.White
        )
    }
}

@Composable
fun DetectedFaces(
    faces: List<Face>,
    sourceInfo: SourceInfo
) {
    Box {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val needToMirror = sourceInfo.isImageFlipped
            for (face in faces) {
                val left =
                    if (needToMirror) size.width - face.boundingBox.right.toFloat() else face.boundingBox.left.toFloat()
                drawRect(
                    color = Color.Green,
                    style = Stroke(2.dp.toPx()),
                    topLeft = Offset(left, face.boundingBox.top.toFloat()),
                    size = Size(
                        face.boundingBox.width().toFloat(),
                        face.boundingBox.height().toFloat()
                    )
                )
            }
        }
    }
}

fun calculateScale(
    constraints: Constraints,
    sourceInfo: SourceInfo,
    scaleType: PreviewScaleType
): Float {
    val heightRatio = constraints.maxHeight.toFloat() / sourceInfo.height
    val widthRatio = constraints.maxWidth.toFloat() / sourceInfo.width
    return when (scaleType) {
        PreviewScaleType.FIT_CENTER -> kotlin.math.min(heightRatio, widthRatio)
        PreviewScaleType.CENTER_CROP -> kotlin.math.max(heightRatio, widthRatio)
    }
}


