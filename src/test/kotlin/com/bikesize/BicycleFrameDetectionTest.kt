package com.bikesize

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.opencv.core.Mat
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import nu.pattern.OpenCV

class BicycleFrameDetectionTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            OpenCV.loadShared()
        }
    }

    @Test
    fun `test BicycleFrameDetectionEngine initialization`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        assertNotNull(engine)
    }

    @Test
    fun `test frame component enum values`() {
        assertEquals(11, FrameComponent.values().size)
        assertTrue(FrameComponent.values().contains(FrameComponent.SEAT_TUBE))
        assertTrue(FrameComponent.values().contains(FrameComponent.TOP_TUBE))
        assertTrue(FrameComponent.values().contains(FrameComponent.DOWN_TUBE))
        assertTrue(FrameComponent.values().contains(FrameComponent.HEAD_TUBE))
        assertTrue(FrameComponent.values().contains(FrameComponent.SEAT_STAY_LEFT))
        assertTrue(FrameComponent.values().contains(FrameComponent.SEAT_STAY_RIGHT))
        assertTrue(FrameComponent.values().contains(FrameComponent.CHAIN_STAY_LEFT))
        assertTrue(FrameComponent.values().contains(FrameComponent.CHAIN_STAY_RIGHT))
        assertTrue(FrameComponent.values().contains(FrameComponent.FORK))
        assertTrue(FrameComponent.values().contains(FrameComponent.SEATPOST))
        assertTrue(FrameComponent.values().contains(FrameComponent.UNKNOWN))
    }

    @Test
    fun `test enhanced DetectedLine with frame component`() {
        val line = DetectedLine(
            10f, 20f, 30f, 40f, 50f, 45f, 0.9f,
            FrameComponent.SEAT_TUBE, 0.8f
        )
        assertEquals(10f, line.x1)
        assertEquals(20f, line.y1)
        assertEquals(30f, line.x2)
        assertEquals(40f, line.y2)
        assertEquals(50f, line.length)
        assertEquals(45f, line.angle)
        assertEquals(0.9f, line.confidence)
        assertEquals(FrameComponent.SEAT_TUBE, line.component)
        assertEquals(0.8f, line.geometryScore)
    }

    @Test
    fun `test DetectionConfig with new frame parameters`() {
        val config = DetectionConfig()
        assertEquals(0.7, config.frameComponentLengthThreshold)
        assertEquals(0.6, config.frameComponentAngleThreshold)
        assertEquals(0.05, config.frameMinComponentLength)
        assertEquals(0.3, config.frameMaxHeadTubeRatio)
    }

    @Test
    fun `test identifyFrameComponents with no lines`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        val wheels = listOf(
            DetectedCircle(100f, 400f, 150f, 0.9f),
            DetectedCircle(600f, 400f, 150f, 0.9f)
        )
        
        val result = engine.identifyFrameComponents(emptyList(), imageData, wheels)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test identifyFrameComponents with insufficient wheels`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        val wheels = listOf(DetectedCircle(100f, 400f, 150f, 0.9f)) // Only one wheel
        val lines = listOf(
            DetectedLine(200f, 200f, 300f, 350f, 180f, 45f, 0.8f)
        )
        
        val result = engine.identifyFrameComponents(lines, imageData, wheels)
        assertTrue(result.isNotEmpty()) // Should fall back to basic geometry
        assertEquals(FrameComponent.UNKNOWN, result[0].component) // Won't identify specific components
    }

    @Test
    fun `test identifyFrameComponents with proper bicycle setup`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        
        // Mock wheels positioned left (rear) and right (front)
        val wheels = listOf(
            DetectedCircle(200f, 400f, 150f, 0.9f), // Rear wheel
            DetectedCircle(700f, 400f, 150f, 0.9f)  // Front wheel
        )
        
        // Mock frame lines with bicycle-like characteristics
        val lines = listOf(
            // Seat tube (vertical, rear-center)
            DetectedLine(350f, 200f, 350f, 400f, 200f, 90f, 0.9f),
            // Top tube (horizontal, above wheels)
            DetectedLine(350f, 200f, 650f, 220f, 300f, 5f, 0.8f),
            // Down tube (diagonal down-forward)
            DetectedLine(350f, 400f, 650f, 350f, 320f, -15f, 0.8f),
            // Head tube (short vertical, front)
            DetectedLine(650f, 220f, 650f, 350f, 130f, 90f, 0.7f),
            // Chain stay (horizontal, near rear wheel)
            DetectedLine(200f, 400f, 350f, 420f, 155f, 7f, 0.7f),
            // Seat stay (diagonal up-back to rear wheel)
            DetectedLine(200f, 380f, 350f, 200f, 220f, 55f, 0.7f)
        )
        
        val result = engine.identifyFrameComponents(lines, imageData, wheels)
        
        assertTrue(result.isNotEmpty())
        
        // Check that some components were identified (not all UNKNOWN)
        val identifiedComponents = result.filter { it.component != FrameComponent.UNKNOWN }
        assertTrue(identifiedComponents.isNotEmpty(), "Should identify at least some frame components")
        
        // Check that geometry scores were calculated
        assertTrue(result.any { it.geometryScore > 0 }, "Should have positive geometry scores")
    }

    @Test
    fun `test bicycle frame constraints filtering`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        
        val wheels = listOf(
            DetectedCircle(200f, 400f, 150f, 0.9f),
            DetectedCircle(700f, 400f, 150f, 0.9f)
        )
        
        val lines = listOf(
            // Good line: reasonable length and angle, in bike area
            DetectedLine(350f, 200f, 450f, 350f, 180f, 45f, 0.8f),
            // Bad line: too short
            DetectedLine(300f, 300f, 305f, 305f, 7f, 45f, 0.8f),
            // Bad line: too horizontal (likely ground)
            DetectedLine(100f, 500f, 800f, 505f, 700f, 2f, 0.8f),
            // Bad line: outside bike area
            DetectedLine(50f, 100f, 100f, 150f, 70f, 45f, 0.8f)
        )
        
        val result = engine.identifyFrameComponents(lines, imageData, wheels)
        
        // Should filter out the bad lines
        assertTrue(result.size < lines.size, "Should filter out lines that don't meet bicycle constraints")
    }

    private fun createMockImageData(): ImageLoader.ImageData {
        return ImageLoader.ImageData(
            original = Mat(),
            grayscale = Mat(),
            preprocessed = Mat(),
            width = 1920,
            height = 1080,
            filePath = "test.jpg"
        )
    }
}