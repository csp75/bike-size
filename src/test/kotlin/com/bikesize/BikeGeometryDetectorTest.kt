package com.bikesize

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nu.pattern.OpenCV

class BikeGeometryDetectorTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            OpenCV.loadShared()
        }
    }

    @Test
    fun `test DetectedCircle data class`() {
        val circle = DetectedCircle(100f, 200f, 50f, 0.8f)
        assertEquals(100f, circle.x)
        assertEquals(200f, circle.y)
        assertEquals(50f, circle.radius)
        assertEquals(0.8f, circle.confidence)
    }

    @Test
    fun `test DetectedLine data class`() {
        val line = DetectedLine(10f, 20f, 30f, 40f, 50f, 45f, 0.9f)
        assertEquals(10f, line.x1)
        assertEquals(20f, line.y1)
        assertEquals(30f, line.x2)
        assertEquals(40f, line.y2)
        assertEquals(50f, line.length)
        assertEquals(45f, line.angle)
        assertEquals(0.9f, line.confidence)
    }

    @Test
    fun `test ImageLoader with non-existent file`() {
        val imageLoader = ImageLoader()
        assertThrows<IllegalArgumentException> {
            imageLoader.loadAndPreprocess("non_existent_file.jpg")
        }
    }

    @Test
    fun `test DetectionConfig default values`() {
        val config = DetectionConfig()
        assertEquals(1.2, config.houghCirclesDp)
        assertEquals(0.125, config.houghCirclesMinDist)
        assertEquals(100.0, config.houghCirclesParam1)
        assertEquals(30.0, config.houghCirclesParam2)
        assertEquals(0.05, config.houghCirclesMinRadius)
        assertEquals(0.33, config.houghCirclesMaxRadius)
        assertEquals(20, config.minLineLength)
        assertEquals(10, config.maxLineGap)
        assertEquals(5.0, config.lineAngleTolerance)
    }

    @Test
    fun `test GeometryCalculator wheelbase calculation with empty wheels`() {
        val calculator = GeometryCalculator()
        val wheels = emptyList<DetectedCircle>()
        val frameLines = emptyList<DetectedLine>()
        val imageData = createMockImageData()
        
        val measurements = calculator.calculateMeasurements(wheels, frameLines, imageData)
        assertEquals(0.0f, measurements.wheelbasePixels)
        assertEquals(0.0f, measurements.averageWheelDiameterPixels)
    }

    @Test
    fun `test GeometryCalculator with two wheels`() {
        val calculator = GeometryCalculator()
        val wheels = listOf(
            DetectedCircle(100f, 400f, 150f, 0.9f),
            DetectedCircle(600f, 400f, 150f, 0.9f)
        )
        val frameLines = emptyList<DetectedLine>()
        val imageData = createMockImageData()
        
        val measurements = calculator.calculateMeasurements(wheels, frameLines, imageData)
        assertEquals(500f, measurements.wheelbasePixels)
        assertEquals(300f, measurements.averageWheelDiameterPixels)
    }

    @Test
    fun `test ConfidenceScores calculation`() {
        val calculator = GeometryCalculator()
        val wheels = listOf(
            DetectedCircle(100f, 400f, 150f, 0.9f),
            DetectedCircle(600f, 400f, 150f, 0.8f)
        )
        val frameLines = listOf(
            DetectedLine(200f, 200f, 400f, 300f, 100f, 45f, 0.7f)
        )
        val imageData = createMockImageData()
        
        val confidenceScores = calculator.calculateConfidenceScores(wheels, frameLines, imageData)
        assertTrue(confidenceScores.wheelDetection > 0.0f)
        assertTrue(confidenceScores.frameDetection > 0.0f)
        assertTrue(confidenceScores.wheelDetection <= 1.0f)
        assertTrue(confidenceScores.frameDetection <= 1.0f)
    }

    @Test
    fun `test Visualizer output filename generation`() {
        val visualizer = Visualizer()
        val outputPath = visualizer.generateOutputFilename("/path/to/bike.jpg", "./results")
        assertTrue(outputPath.contains("bike_detected.jpg"))
        assertTrue(outputPath.contains("results"))
    }

    private fun createMockImageData(): ImageLoader.ImageData {
        // This creates a minimal mock object for testing
        // In a real test, you might want to create actual Mat objects
        return ImageLoader.ImageData(
            original = org.opencv.core.Mat(),
            grayscale = org.opencv.core.Mat(),
            preprocessed = org.opencv.core.Mat(),
            width = 1920,
            height = 1080,
            filePath = "test.jpg"
        )
    }
}