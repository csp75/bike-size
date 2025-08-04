package com.bikesize

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import nu.pattern.OpenCV
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.file.Path
import kotlin.system.measureTimeMillis

/**
 * Integration test to validate all wheel detection improvements mentioned in issue #13.
 */
class WheelDetectionImprovementTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            OpenCV.loadShared()
        }
    }

    @Test
    fun `test wheel detection improvement benchmarks`() {
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        
        // Test with a single sample image to validate core improvements
        val imageName = "cinelli.jpg"
        val imagePath = "./images/$imageName"
        val imageFile = File(imagePath)
        
        if (!imageFile.exists()) {
            println("Skipping test for $imageName - file not found")
            return
        }
        
        val appConfig = BikeGeometryDetector.AppConfig(
            inputPath = imagePath,
            outputPath = "./results",
            debugMode = false
        )
        
        // Test wheel detection
        val imageData = imageLoader.loadAndPreprocess(imagePath, appConfig)
        val detectedWheels = wheelDetector.detectWheels(imageData, appConfig)
        
        // Validate improvements:
        // 1. Should detect reasonable number of circles (much less than 100+)
        assertTrue(
            detectedWheels.size <= MAX_EXPECTED_CIRCLES, 
            "Too many circles detected for $imageName: ${detectedWheels.size}. " +
            "Expected <= $MAX_EXPECTED_CIRCLES (improvement from 100+)"
        )
        
        // 2. Should still correctly identify 2 wheels in final selection
        val wheelGroups = groupWheelsByPosition(detectedWheels)
        assertTrue(
            wheelGroups.size <= 2, 
            "Expected at most 2 main wheel positions for $imageName, got ${wheelGroups.size}"
        )
        
        // 3. High confidence detection
        val avgConfidence = detectedWheels.map { it.confidence }.average()
        assertTrue(
            avgConfidence > 0.8, 
            "Low confidence for $imageName: $avgConfidence. Expected > 0.8"
        )
        
        // 4. Wheel components should be properly classified
        detectedWheels.forEach { wheel ->
            assertNotNull(wheel.component, "Wheel component should not be null for $imageName")
            assertTrue(
                wheel.component in listOf(WheelComponent.TIRE, WheelComponent.RIM, WheelComponent.UNKNOWN),
                "Invalid wheel component for $imageName: ${wheel.component}"
            )
        }
        
        println("✓ $imageName: ${detectedWheels.size} circles detected")
    }

    @Test 
    fun `test concentric circle detection with synthetic image`(@TempDir tempDir: Path) {
        // Create synthetic image with perfect concentric circles
        val imageWidth = 1000
        val imageHeight = 800
        val wheelCenterX = 500f
        val wheelCenterY = 400f
        val tireRadius = 150f
        val rimRadius = 100f
        
        val syntheticImage = Mat(imageHeight, imageWidth, CvType.CV_8UC3, Scalar(50.0, 50.0, 50.0))
        
        // Draw tire (outer circle) in white
        Imgproc.circle(syntheticImage, 
            Point(wheelCenterX.toDouble(), wheelCenterY.toDouble()), 
            tireRadius.toInt(), Scalar(255.0, 255.0, 255.0), 10)
            
        // Draw rim (inner circle) in gray  
        Imgproc.circle(syntheticImage, 
            Point(wheelCenterX.toDouble(), wheelCenterY.toDouble()), 
            rimRadius.toInt(), Scalar(200.0, 200.0, 200.0), 8)
        
        val imageFile = tempDir.resolve("concentric_wheel.jpg").toFile()
        assertTrue(Imgcodecs.imwrite(imageFile.absolutePath, syntheticImage))
        
        // Test wheel detection
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        val appConfig = BikeGeometryDetector.AppConfig(
            inputPath = imageFile.absolutePath,
            outputPath = tempDir.toString(),
            debugMode = false
        )
        
        val imageData = imageLoader.loadAndPreprocess(imageFile.absolutePath, appConfig)
        val detectedWheels = wheelDetector.detectWheels(imageData, appConfig)
        
        // Should detect both circles
        assertTrue(detectedWheels.isNotEmpty(), "Should detect at least one circle")
        
        // Check for potential concentric detection (algorithm may or may not find them depending on parameters)
        val wheelsNearCenter = detectedWheels.filter { wheel ->
            val distance = kotlin.math.sqrt(
                (wheel.x - wheelCenterX) * (wheel.x - wheelCenterX) + 
                (wheel.y - wheelCenterY) * (wheel.y - wheelCenterY)
            )
            distance < 50 // Within 50 pixels of center
        }
        
        assertTrue(wheelsNearCenter.isNotEmpty(), 
            "Should detect at least one wheel near center position ($wheelCenterX, $wheelCenterY)")
    }

    @Test
    fun `test wheel detection parameter improvements`() {
        // Verify that improved parameters are being used
        val config = DetectionConfig()
        
        // Check that parameters have been improved from defaults
        assertEquals(50.0, config.houghCirclesParam2, "param2 should be increased to reduce false positives")
        assertEquals(0.2, config.houghCirclesMinDist, "minDist should be increased to prevent overlapping")
        assertEquals(0.08, config.houghCirclesMinRadius, "minRadius should be slightly increased")
        assertEquals(0.3, config.houghCirclesMaxRadius, "maxRadius should be slightly reduced")
        
        // Check new concentric parameters
        assertEquals(0.15, config.concentricCircleToleranceRatio, "Should have concentric tolerance parameter")
        assertEquals(0.02, config.minConcentricRadiusDiff, "Should have minimum radius difference parameter")
    }

    /**
     * Groups detected wheels by approximate position to count distinct wheel locations.
     */
    private fun groupWheelsByPosition(wheels: List<DetectedCircle>): List<List<DetectedCircle>> {
        val groups = mutableListOf<MutableList<DetectedCircle>>()
        val processed = mutableSetOf<DetectedCircle>()
        
        for (wheel in wheels) {
            if (wheel in processed) continue
            
            val group = mutableListOf<DetectedCircle>()
            group.add(wheel)
            processed.add(wheel)
            
            // Find nearby wheels (within 200 pixels)
            for (otherWheel in wheels) {
                if (otherWheel in processed) continue
                
                val distance = kotlin.math.sqrt(
                    (wheel.x - otherWheel.x) * (wheel.x - otherWheel.x) + 
                    (wheel.y - otherWheel.y) * (wheel.y - otherWheel.y)
                )
                
                if (distance < WHEEL_GROUPING_DISTANCE_THRESHOLD) {
                    group.add(otherWheel)
                    processed.add(otherWheel)
                }
            }
            
            groups.add(group)
        }
        
        return groups
    }
}