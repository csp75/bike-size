package com.bikesize

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import nu.pattern.OpenCV
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.file.Path

/**
 * Test class for validating wheel detection with synthetic wheel images.
 * This test creates simple synthetic wheel images and verifies that the 
 * wheel detection algorithm can find the same circles.
 */
class SyntheticWheelTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            OpenCV.loadShared()
        }
    }

    @Test
    fun `test wheel detection with synthetic single wheel image`(@TempDir tempDir: Path) {
        // Create a synthetic image with a single wheel (tire + rim concentric circles)
        val imageWidth = 800
        val imageHeight = 600
        val wheelCenterX = 400f
        val wheelCenterY = 300f
        val tireRadius = 120f
        val rimRadius = 80f
        
        val syntheticImage = createSyntheticWheelImage(
            imageWidth, imageHeight,
            listOf(
                Pair(Point(wheelCenterX.toDouble(), wheelCenterY.toDouble()), tireRadius.toDouble()),
                Pair(Point(wheelCenterX.toDouble(), wheelCenterY.toDouble()), rimRadius.toDouble())
            )
        )
        
        // Save the synthetic image
        val imageFile = tempDir.resolve("synthetic_wheel.jpg").toFile()
        assertTrue(Imgcodecs.imwrite(imageFile.absolutePath, syntheticImage), "Failed to save synthetic image")
        
        // Load and test wheel detection
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        val appConfig = BikeGeometryDetector.AppConfig(
            inputPath = imageFile.absolutePath,
            outputPath = tempDir.toString(),
            debugMode = false
        )
        
        val imageData = imageLoader.loadAndPreprocess(imageFile.absolutePath, appConfig)
        val detectedWheels = wheelDetector.detectWheels(imageData, appConfig)
        
        // Should detect at least one wheel component
        assertTrue(detectedWheels.isNotEmpty(), "No wheels detected in synthetic image")
        
        // Check if we found a wheel near the expected position
        val foundWheel = detectedWheels.find { wheel ->
            val distance = kotlin.math.sqrt(
                (wheel.x - wheelCenterX) * (wheel.x - wheelCenterX) + 
                (wheel.y - wheelCenterY) * (wheel.y - wheelCenterY)
            )
            distance < 50 // Allow 50 pixel tolerance
        }
        
        assertTrue(foundWheel != null, "No wheel found near expected position ($wheelCenterX, $wheelCenterY)")
    }

    @Test
    fun `test wheel detection with synthetic two wheel image`(@TempDir tempDir: Path) {
        // Create a synthetic image with two wheels representing a bicycle
        val imageWidth = 1200
        val imageHeight = 600
        val wheel1CenterX = 300f
        val wheel2CenterX = 900f
        val wheelCenterY = 400f
        val tireRadius = 100f
        val rimRadius = 70f
        
        val syntheticImage = createSyntheticWheelImage(
            imageWidth, imageHeight,
            listOf(
                // First wheel (tire and rim)
                Pair(Point(wheel1CenterX.toDouble(), wheelCenterY.toDouble()), tireRadius.toDouble()),
                Pair(Point(wheel1CenterX.toDouble(), wheelCenterY.toDouble()), rimRadius.toDouble()),
                // Second wheel (tire and rim)
                Pair(Point(wheel2CenterX.toDouble(), wheelCenterY.toDouble()), tireRadius.toDouble()),
                Pair(Point(wheel2CenterX.toDouble(), wheelCenterY.toDouble()), rimRadius.toDouble())
            )
        )
        
        // Save the synthetic image
        val imageFile = tempDir.resolve("synthetic_bike.jpg").toFile()
        assertTrue(Imgcodecs.imwrite(imageFile.absolutePath, syntheticImage), "Failed to save synthetic image")
        
        // Load and test wheel detection
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        val appConfig = BikeGeometryDetector.AppConfig(
            inputPath = imageFile.absolutePath,
            outputPath = tempDir.toString(),
            debugMode = false
        )
        
        val imageData = imageLoader.loadAndPreprocess(imageFile.absolutePath, appConfig)
        val detectedWheels = wheelDetector.detectWheels(imageData, appConfig)
        
        // Should detect exactly two wheels (or wheel components)
        assertTrue(detectedWheels.size >= 2, "Expected at least 2 wheel components, got ${detectedWheels.size}")
        
        // Check if we found wheels near both expected positions
        val foundWheel1 = detectedWheels.find { wheel ->
            val distance = kotlin.math.sqrt(
                (wheel.x - wheel1CenterX) * (wheel.x - wheel1CenterX) + 
                (wheel.y - wheelCenterY) * (wheel.y - wheelCenterY)
            )
            distance < 50
        }
        
        val foundWheel2 = detectedWheels.find { wheel ->
            val distance = kotlin.math.sqrt(
                (wheel.x - wheel2CenterX) * (wheel.x - wheel2CenterX) + 
                (wheel.y - wheelCenterY) * (wheel.y - wheelCenterY)
            )
            distance < 50
        }
        
        assertTrue(foundWheel1 != null, "No wheel found near first expected position ($wheel1CenterX, $wheelCenterY)")
        assertTrue(foundWheel2 != null, "No wheel found near second expected position ($wheel2CenterX, $wheelCenterY)")
    }

    /**
     * Creates a synthetic image with circles representing wheels.
     * Uses minimal colors as suggested in the issue.
     */
    private fun createSyntheticWheelImage(
        width: Int, 
        height: Int, 
        circles: List<Pair<Point, Double>>
    ): Mat {
        // Create a black background image
        val image = Mat(height, width, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
        
        // Draw circles with minimal colors
        for ((center, radius) in circles) {
            // Draw filled circle in white for the wheel area
            Imgproc.circle(image, center, radius.toInt(), Scalar(255.0, 255.0, 255.0), -1)
            
            // Draw black outline to create contrast
            Imgproc.circle(image, center, radius.toInt(), Scalar(0.0, 0.0, 0.0), 3)
        }
        
        return image
    }
}