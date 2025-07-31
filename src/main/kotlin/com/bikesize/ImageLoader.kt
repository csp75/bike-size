package com.bikesize

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Handles image loading and preprocessing operations.
 */
class ImageLoader {
    private val logger = LoggerFactory.getLogger(ImageLoader::class.java)

    /**
     * Generates debug filename based on base image name.
     */
    private fun generateDebugFilename(baseImagePath: String, outputDir: String, suffix: String, extension: String = "jpg"): String {
        val baseFile = File(baseImagePath)
        val baseName = baseFile.nameWithoutExtension
        return File(outputDir, "${baseName}_${suffix}.${extension}").absolutePath
    }

    /**
     * Loads an image from file and performs preprocessing.
     * 
     * @param filePath Path to the input image file
     * @param config Application configuration including debug settings
     * @return ImageData containing original and processed versions
     * @throws IllegalArgumentException if file doesn't exist or can't be loaded
     */
    fun loadAndPreprocess(filePath: String, appConfig: BikeGeometryDetector.AppConfig): ImageData {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Input file does not exist: $filePath")
        }

        logger.info("Loading image from: $filePath")
        
        // Load the original image
        val originalImage = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_COLOR)
        if (originalImage.empty()) {
            throw IllegalArgumentException("Failed to load image: $filePath")
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
            val debugPath = generateDebugFilename(filePath, appConfig.outputPath, "grayscale")
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
            val debugPath = generateDebugFilename(filePath, appConfig.outputPath, "blurred")
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
            filePath = filePath
        )
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
        val filePath: String
    )
}