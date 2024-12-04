package com.dicoding.acnescan.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.dicoding.acnescan.database.HistoryEntity
import com.dicoding.acnescan.databinding.FragmentCameraBinding
import com.dicoding.acnescan.ui.history.HistoryViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var historyViewModel: HistoryViewModel
    private var isFrontCamera = true // Menandakan kamera yang aktif (depan atau belakang)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi ViewModel
        historyViewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[HistoryViewModel::class.java]

        // Memastikan permission kamera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up tombol capture
        binding.captureButton.setOnClickListener {
            takePhoto()
        }

        // Set up tombol gallery
        binding.galleryButton.setOnClickListener {
            openGallery()
        }

        // Set up tombol switch kamera
        binding.switchCameraButton.setOnClickListener {
            isFrontCamera = !isFrontCamera // Toggle kamera depan / belakang
            startCamera() // Restart kamera dengan kamera yang baru dipilih
        }

        // Inisialisasi executor kamera
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Menentukan selector kamera berdasarkan status kamera depan/belakang
            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()

                // Bind use cases ke lifecycle
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
//                    val savedUri = Uri.fromFile(photoFile)

                    // Koreksi orientasi gambar
                    val correctedImagePath = correctImageOrientation(photoFile)

                    // Simpan ke database Room
                    saveToDatabase(
                        title = "Captured Image",
                        description = "Photo taken on ${System.currentTimeMillis()}",
                        timestamp = System.currentTimeMillis().toString(),
                        imagePath = correctedImagePath
                    )

                    // Kirim gambar ke AnalysisActivity dengan path baru
                    val intent = Intent(requireContext(), AnalysisActivity::class.java)
                    intent.putExtra(AnalysisActivity.EXTRA_IMAGE_PATH, correctedImagePath) // Path gambar yang sudah dikoreksi

                    startActivity(intent)

                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun correctImageOrientation(file: File): String {
        // Decode gambar dari file
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        // Membaca informasi orientasi EXIF
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        // Rotasi gambar berdasarkan orientasi EXIF dan apakah kamera depan atau belakang digunakan
        val rotatedBitmap = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }

        // Jika gambar diproses menggunakan kamera depan, balik gambar secara horizontal
        val finalBitmap = if (isFrontCamera) {
            flipBitmapHorizontally(rotatedBitmap)
        } else {
            rotatedBitmap
        }

        // Simpan gambar yang sudah diproses ke file baru
        val correctedFile = File(file.parent, "corrected_${file.name}")
        FileOutputStream(correctedFile).use { outputStream ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

        // Mengembalikan path file yang sudah diperbaiki
        return correctedFile.absolutePath
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipBitmapHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.preScale(-1f, 1f) // Membalik gambar secara horizontal
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GALLERY && resultCode == AppCompatActivity.RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null) {
                // Kirim URI gambar ke AnalysisActivity
                val intent = Intent(requireContext(), AnalysisActivity::class.java)
                intent.putExtra(AnalysisActivity.EXTRA_IMAGE_URI, imageUri.toString())
                startActivity(intent)
            }
        }
    }

    private fun saveToDatabase(title: String, description: String, timestamp: String, imagePath: String) {
        val historyEntity = HistoryEntity(
            title = title,
            description = description,
            timestamp = timestamp,
            imagePath = imagePath
        )

        // Gunakan ViewModel untuk menyimpan data
        historyViewModel.insert(historyEntity)

        Toast.makeText(requireContext(), "Image saved to history", Toast.LENGTH_SHORT).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Menangani hasil permintaan izin
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Jika izin diberikan, mulai kamera
                startCamera()
            } else {
                // Jika izin ditolak, tampilkan pesan
                Toast.makeText(
                    requireContext(),
                    "Permission denied. Camera cannot be used.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_GALLERY)
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_GALLERY = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}