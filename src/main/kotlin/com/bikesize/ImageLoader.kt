package com.bikesize

import okhttp3.OkHttpClient
import okhttp3.Request
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Handles image loading and preprocessing operations.
 */
class ImageLoader {
    private val logger = LoggerFactory.getLogger(ImageLoader::class.java)
    private val httpClient = OkHttpClient()

    /**
     * Generates debug filename based on base image name with versioning support.
     */
    private fun generateDebugFilename(baseImagePath: String, outputDir: String, suffix: String, extension: String = "jpg", overwrite: Boolean = false): String {
        val baseFile = File(baseImagePath)
        val baseName = baseFile.nameWithoutExtension
        return generateVersionedFilename(outputDir, "${baseName}_${suffix}", extension, overwrite)
    }

    /**
     * Generates a versioned filename that either overwrites or adds incrementing suffix.
     */
    private fun generateVersionedFilename(outputDir: String, baseName: String, extension: String, overwrite: Boolean): String {
        val baseFile = File(outputDir, "$baseName.$extension")
        
        if (overwrite || !baseFile.exists()) {
            return baseFile.absolutePath
        }
        
        // File exists and overwrite is false, find next available version
        var counter = 1
        var versionedFile: File
        do {
            versionedFile = File(outputDir, "$baseName-$counter.$extension")
            counter++
        } while (versionedFile.exists())
        
        return versionedFile.absolutePath
    }

    /**
     * Loads an image from file and performs preprocessing.
     * 
     * @param inputPath Path to the input image file or URL
     * @param appConfig Application configuration including debug settings
     * @return ImageData containing original and processed versions
     * @throws IllegalArgumentException if file doesn't exist or can't be loaded
     */
    fun loadAndPreprocess(inputPath: String, appConfig: BikeGeometryDetector.AppConfig): ImageData {
        val isUrl = inputPath.startsWith("http://") || inputPath.startsWith("https://")
        val filePath = if (isUrl) {
            downloadImageFromUrl(inputPath)
        } else {
            validateLocalFile(inputPath)
            inputPath
      }

        logger.info("Loading image from: $inputPath")
        
        // Load the original image
        val originalImage = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_COLOR)
        if (originalImage.empty()) {
            throw IllegalArgumentException("Failed to load image: $inputPath")
        }

        // Validate image dimensions
        val height = originalImage.rows()
        val width = originalImage.cols()
        logger.info("Image dimensions: ${width}x${height}")
        
        if (width < 1024 || height < 768) {
            logger.warn("Image resolution (${width}x${height}) is below recommended minimum (1024x768)")
        }
        if (width > 4096 || height > 3072) {
            logger.warn("Image resolution (${width}x${height}) is above recommended maximum (4096x3072)")
        }

        // Convert to grayscale
        val grayscaleImage = Mat()
        Imgproc.cvtColor(originalImage, grayscaleImage, Imgproc.COLOR_BGR2GRAY)
        logger.debug("Converted to grayscale")
        
        // Save debug image if debug mode is enabled
        if (appConfig.debugMode) {
            val debugPath = generateDebugFilename(filePath, appConfig.outputPath, "grayscale", "jpg", appConfig.overwrite)
            if (Imgcodecs.imwrite(debugPath, grayscaleImage)) {
                logger.info("Debug: Saved grayscale image to: $debugPath")
            }
        }

        // Apply Gaussian blur to reduce noise
        val blurredImage = Mat()
        val kernelSize = Size(5.0, 5.0)
        Imgproc.GaussianBlur(grayscaleImage, blurredImage, kernelSize, 0.0)
        logger.debug("Applied Gaussian blur with 5x5 kernel")
        
        // Save debug image if debug mode is enabled
        if (appConfig.debugMode) {
            val debugPath = generateDebugFilename(filePath, appConfig.outputPath, "blurred", "jpg", appConfig.overwrite)
            if (Imgcodecs.imwrite(debugPath, blurredImage)) {
                logger.info("Debug: Saved blurred image to: $debugPath")
            }
        }

        return ImageData(
            original = originalImage,
            grayscale = grayscaleImage,
            preprocessed = blurredImage,
            width = width,
            height = height,
            filePath = inputPath,
            isUrl = isUrl,
            localFilePath = filePath
        )
    }

    /**
     * Validates that a local file exists.
     */
    private fun validateLocalFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Input file does not exist: $filePath")
        }
    }

    /**
     * Downloads an image from URL to a temporary file.
     */
    private fun downloadImageFromUrl(url: String): String {
        try {
            logger.info("Downloading image from URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download image: HTTP ${response.code}")
                }
                
                val contentType = response.header("Content-Type")
                val extension = when {
                    contentType?.contains("jpeg") == true || contentType?.contains("jpg") == true -> "jpg"
                    contentType?.contains("png") == true -> "png"
                    url.lowercase().endsWith(".jpg") || url.lowercase().endsWith(".jpeg") -> "jpg"
                    url.lowercase().endsWith(".png") -> "png"
                    else -> "jpg" // default
                }
                
                // Create temporary file
                val tempFile = Files.createTempFile("bike_image_", ".$extension").toFile()
                tempFile.deleteOnExit()
                
                response.body?.byteStream()?.use { inputStream ->
                    Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                
                logger.info("Image downloaded to temporary file: ${tempFile.absolutePath}")
                return tempFile.absolutePath
            }
        } catch (e: Exception) {
            logger.error("Failed to download image from URL: $url", e)
            throw IllegalArgumentException("Failed to download image from URL: $url - ${e.message}", e)
        }
    }

    /**
     * Data class to hold different versions of the loaded image.
     */
    data class ImageData(
        val original: Mat,
        val grayscale: Mat,
        val preprocessed: Mat,
        val width: Int,
        val height: Int,
        val filePath: String,
        val isUrl: Boolean = false,
        val localFilePath: String = filePath
    )
}