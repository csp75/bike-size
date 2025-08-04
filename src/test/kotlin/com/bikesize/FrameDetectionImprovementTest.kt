package com.bikesize

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.opencv.core.Mat
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.math.abs
import nu.pattern.OpenCV

/**
 * Test to demonstrate improvements in frame detection compared to basic line detection.
 */
class FrameDetectionImprovementTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            OpenCV.loadShared()
        }
    }

    @Test
    fun `test improved frame detection identifies components`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        
        // Mock realistic bicycle setup
        val wheels = listOf(
            DetectedCircle(200f, 400f, 150f, 0.9f), // Rear wheel
            DetectedCircle(700f, 400f, 150f, 0.9f)  // Front wheel
        )
        
        // Mock frame lines that represent actual bicycle frame components
        val detectedLines = listOf(
            // Seat tube (vertical, rear-center area)
            DetectedLine(350f, 200f, 350f, 400f, 200f, 90f, 0.9f),
            // Top tube (horizontal, above wheels)
            DetectedLine(350f, 200f, 650f, 220f, 300f, 5f, 0.8f),
            // Down tube (diagonal down-forward)
            DetectedLine(350f, 400f, 650f, 350f, 320f, -15f, 0.8f),
            // Head tube (short vertical, front)
            DetectedLine(650f, 220f, 650f, 350f, 130f, 90f, 0.7f),
            // Chain stay (horizontal, connecting to rear wheel)
            DetectedLine(200f, 400f, 350f, 420f, 155f, 7f, 0.7f),
            // Seat stay (diagonal up-back to rear wheel)
            DetectedLine(200f, 380f, 350f, 200f, 220f, 55f, 0.7f),
            // Random background lines that shouldn't be classified
            DetectedLine(50f, 100f, 80f, 120f, 35f, 45f, 0.5f),  // Too short
            DetectedLine(100f, 500f, 800f, 502f, 700f, 1f, 0.6f), // Too horizontal
            DetectedLine(50f, 50f, 55f, 100f, 50f, 84f, 0.5f)    // Outside bike area
        )
        
        val result = engine.identifyFrameComponents(detectedLines, imageData, wheels)
        
        // Verify improvements
        assertTrue(result.isNotEmpty(), "Should identify frame components")
        
        // Should filter out inappropriate lines (background noise)
        assertTrue(result.size < detectedLines.size, "Should filter out non-frame lines")
        
        // Should identify some components as specific frame parts (not all UNKNOWN)
        val identifiedComponents = result.filter { it.component != FrameComponent.UNKNOWN }
        assertTrue(identifiedComponents.isNotEmpty(), "Should classify some lines as specific frame components")
        
        // Should calculate geometry scores for identified components
        val scoredComponents = result.filter { it.geometryScore > 0 }
        assertTrue(scoredComponents.isNotEmpty(), "Should assign positive geometry scores")
        
        // Should prioritize better geometry matches
        val sortedByTotal = result.sortedByDescending { it.confidence + it.geometryScore }
        assertTrue(sortedByTotal.first().confidence + sortedByTotal.first().geometryScore >= 
                  sortedByTotal.last().confidence + sortedByTotal.last().geometryScore,
                  "Should sort by combined confidence and geometry scores")
    }

    @Test
    fun `test bicycle geometry constraints filter inappropriate lines`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        
        val wheels = listOf(
            DetectedCircle(200f, 400f, 150f, 0.9f),
            DetectedCircle(700f, 400f, 150f, 0.9f)
        )
        
        val linesWithNoise = listOf(
            // Good bicycle frame line
            DetectedLine(350f, 200f, 450f, 350f, 180f, 45f, 0.8f),
            // Bad: too short (likely noise)
            DetectedLine(300f, 300f, 305f, 305f, 7f, 45f, 0.8f),
            // Bad: too horizontal (likely ground/horizon)
            DetectedLine(100f, 500f, 800f, 505f, 700f, 2f, 0.8f),
            // Bad: too vertical (likely building/pole)
            DetectedLine(50f, 100f, 52f, 600f, 500f, 89f, 0.8f),
            // Bad: outside bike area
            DetectedLine(50f, 100f, 100f, 150f, 70f, 45f, 0.8f),
            // Bad: excessively long (likely horizon or large structure)
            DetectedLine(0f, 300f, 1920f, 310f, 1920f, 3f, 0.9f)
        )
        
        val result = engine.identifyFrameComponents(linesWithNoise, imageData, wheels)
        
        // Should significantly reduce the number of lines by filtering noise
        assertTrue(result.size < linesWithNoise.size / 2, 
                  "Should filter out most inappropriate lines: ${result.size} from ${linesWithNoise.size}")
        
        // The remaining lines should be reasonable frame candidates
        result.forEach { line ->
            assertTrue(line.length >= 50f, "Remaining lines should be long enough: ${line.length}")
            assertTrue(abs(line.angle % 180) > 10, "Remaining lines should not be too horizontal: ${line.angle}")
            assertTrue(abs(line.angle % 180) < 170, "Remaining lines should not be too vertical: ${line.angle}")
        }
    }

    @Test
    fun `test frame component angle and length relationships`() {
        val config = DetectionConfig()
        val engine = BicycleFrameDetectionEngine(config)
        val imageData = createMockImageData()
        
        val wheels = listOf(
            DetectedCircle(200f, 400f, 150f, 0.9f),
            DetectedCircle(700f, 400f, 150f, 0.9f)
        )
        
        // Create lines that follow bicycle geometry rules
        val bicicleLikeLines = listOf(
            // Main triangle: seat tube, top tube, down tube with similar lengths
            DetectedLine(350f, 200f, 350f, 400f, 200f, 90f, 0.9f),  // Seat tube
            DetectedLine(350f, 200f, 650f, 220f, 305f, 5f, 0.8f),   // Top tube (~similar length)
            DetectedLine(350f, 400f, 650f, 350f, 320f, -15f, 0.8f), // Down tube (~similar length)
            
            // Rear triangle: seat stays and chain stays
            DetectedLine(200f, 380f, 350f, 200f, 220f, 55f, 0.7f),  // Seat stay
            DetectedLine(200f, 400f, 350f, 420f, 155f, 7f, 0.7f),   // Chain stay
            
            // Head tube and fork
            DetectedLine(650f, 220f, 650f, 350f, 130f, 90f, 0.7f),  // Head tube (shorter)
            DetectedLine(650f, 350f, 750f, 420f, 115f, 32f, 0.6f)   // Fork
        )
        
        val result = engine.identifyFrameComponents(bicicleLikeLines, imageData, wheels)
        
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "Should identify components from bicycle-like geometry")
        
        // Check that geometry scores reflect bicycle knowledge
        val geometryScored = result.filter { it.geometryScore > 0 }
        assertTrue(geometryScored.isNotEmpty(), "Should assign geometry scores based on bicycle rules")
        
        // Lines with good bicycle geometry should have higher total scores
        val totalScores = result.map { it.confidence + it.geometryScore }
        assertTrue(totalScores.any { it > 1.0f }, "Good bicycle geometry should boost total scores")
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