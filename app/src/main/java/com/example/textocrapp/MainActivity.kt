package com.example.textocrapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.Result.Companion.success
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var cameraImage: ImageView
    private lateinit var captureImageButton: Button
    private lateinit var resultText: TextView
    private lateinit var copyTextButton: Button

    private var currentPhotoPath: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        cameraImage = findViewById(R.id.cameraImage)
        captureImageButton = findViewById(R.id.captureImageButton)
        resultText = findViewById(R.id.resultText)
        copyTextButton = findViewById(R.id.copyTextButton)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                captureImage()
            } else {
                Toast.makeText(this, "Camera Permission Needed", Toast.LENGTH_SHORT).show()
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoPath?.let { path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    cameraImage.setImageBitmap(bitmap)
                    recogniseText(bitmap)
                }
            }
        }

        captureImageButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // 🔁 Restore state after rotation
        if (savedInstanceState != null) {
            resultText.text = savedInstanceState.getString("ocr_result_text", "")
            copyTextButton.visibility = if (savedInstanceState.getBoolean("copy_button_visible", false))
                View.VISIBLE else View.INVISIBLE

            currentPhotoPath = savedInstanceState.getString("photo_path")
            currentPhotoPath?.let { path ->
                val bitmap = BitmapFactory.decodeFile(path)
                cameraImage.setImageBitmap(bitmap)
            }
        }
    }

    // 🧠 Save state before rotation
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("ocr_result_text", resultText.text.toString())
        outState.putBoolean("copy_button_visible", copyTextButton.isVisible)
        outState.putString("photo_path", currentPhotoPath)
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile("JPEG_${timeStamp}", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun captureImage() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred making image file", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            val photoUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun recogniseText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image).addOnSuccessListener { ocrText ->
            resultText.text = ocrText.text
            resultText.movementMethod = ScrollingMovementMethod()
            copyTextButton.visibility = Button.VISIBLE
            copyTextButton.setOnClickListener {
                val clipboard = ContextCompat.getSystemService(this, android.content.ClipboardManager::class.java)
                val clip = android.content.ClipData.newPlainText("recognized text", ocrText.text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "No text recognized: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}