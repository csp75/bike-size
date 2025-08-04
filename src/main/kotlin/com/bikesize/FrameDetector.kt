package com.bikesize

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.*

/**
 * Detects bicycle frame tubes using Line Segment Detector (LSD) with bicycle geometry knowledge.
 */
class FrameDetector(private val config: DetectionConfig = DetectionConfig()) {
    private val logger = LoggerFactory.getLogger(FrameDetector::class.java)

    /**
     * Detects frame tubes in the preprocessed image.
     * 
     * @param imageData The loaded and preprocessed image data
     * @param wheelPositions Detected wheel positions to help filter relevant lines
     * @param config Application configuration including debug settings
     * @return List of detected line segments representing frame tubes
     */
    fun detectFrameLines(
        imageData: ImageLoader.ImageData,
        wheelPositions: List<DetectedCircle>,
        appConfig: BikeGeometryDetector.AppConfig
    ): List<DetectedLine> {
        logger.info("Starting enhanced frame detection using bicycle geometry knowledge")
        
        // Use OpenCV's built-in line detection instead of LSD for better compatibility
        val lines = Mat()
        
        // Apply Canny edge detection first for better line detection
        val edges = Mat()
        Imgproc.Canny(imageData.preprocessed, edges, 50.0, 150.0)
        
        // Save debug edge image if debug mode is enabled
        if (appConfig.debugMode) {
            val debugPath = FileUtils.generateDebugFilename(imageData.filePath, appConfig.outputPath, "frame_edges", "jpg", appConfig.overwrite)
            if (Imgcodecs.imwrite(debugPath, edges)) {
                logger.info("Debug: Saved edge detection image to: $debugPath")
            }
        }
        
        // Detect lines using HoughLinesP (Probabilistic Hough Transform)
        Imgproc.HoughLinesP(
            edges,
            lines,
            1.0, // rho (distance resolution)
            PI / 180, // theta (angle resolution)
            50, // threshold
            config.minLineLength.toDouble(),
            config.maxLineGap.toDouble()
        )

        val detectedLines = mutableListOf<DetectedLine>()
        
        // Process detected lines
        if (lines.rows() > 0) {
            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0)
                val x1 = line[0].toFloat()
                val y1 = line[1].toFloat()
                val x2 = line[2].toFloat()
                val y2 = line[3].toFloat()
                
                val length = sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
                val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()) * 180 / PI
                
                // Filter lines by minimum length
                if (length >= config.minLineLength) {
                    val confidence = calculateLineConfidence(x1, y1, x2, y2, length, angle.toFloat(), imageData, wheelPositions)
                    detectedLines.add(DetectedLine(x1, y1, x2, y2, length, angle.toFloat(), confidence))
                }
            }
        }

        logger.info("Detected ${detectedLines.size} line segments")
        
        // Save debug image with all detected lines if debug mode is enabled
        if (appConfig.debugMode) {
            val debugImage = imageData.original.clone()
            for (line in detectedLines) {
                // Draw line in blue
                Imgproc.line(debugImage, 
                           Point(line.x1.toDouble(), line.y1.toDouble()),
                           Point(line.x2.toDouble(), line.y2.toDouble()),
                           Scalar(255.0, 0.0, 0.0), 2)
            }
            val debugPath = FileUtils.generateDebugFilename(imageData.filePath, appConfig.outputPath, "frame_lines", "jpg", appConfig.overwrite)
            if (Imgcodecs.imwrite(debugPath, debugImage)) {
                logger.info("Debug: Saved frame lines detection image with ${detectedLines.size} lines to: $debugPath")
            }
        }
        
        // Apply bicycle frame detection logic
        val frameDetectionEngine = BicycleFrameDetectionEngine(config)
        val frameLines = frameDetectionEngine.identifyFrameComponents(detectedLines, imageData, wheelPositions)
        
        logger.info("Identified ${frameLines.size} frame tubes using bicycle geometry")
        return frameLines
    }

    /**
     * Calculates confidence score for a detected line segment.
     */
    private fun calculateLineConfidence(
        x1: Float, y1: Float, x2: Float, y2: Float,
        length: Float, angle: Float,
        imageData: ImageLoader.ImageData,
        wheelPositions: List<DetectedCircle>
    ): Float {
        var confidence = 0.5f
        
        // Favor longer lines
        val maxPossibleLength = sqrt(imageData.width.toFloat().pow(2) + imageData.height.toFloat().pow(2))
        val lengthScore = length / maxPossibleLength
        confidence += lengthScore * 0.3f
        
        // Favor lines that are not too horizontal or vertical (typical bike frame angles)
        val absAngle = abs(angle % 180)
        val angleDeviation = minOf(absAngle, 180 - absAngle)
        if (angleDeviation in 15.0..75.0) {
            confidence += 0.2f
        }
        
        // Favor lines that are positioned near the bike area (between wheels)
        if (wheelPositions.size >= 2) {
            val midX = (x1 + x2) / 2
            val midY = (y1 + y2) / 2
            
            val leftWheel = wheelPositions.minByOrNull { it.x }
            val rightWheel = wheelPositions.maxByOrNull { it.x }
            
            if (leftWheel != null && rightWheel != null) {
                if (midX >= leftWheel.x && midX <= rightWheel.x) {
                    confidence += 0.3f
                    
                    // Extra bonus for lines in the frame triangle area
                    val avgWheelY = (leftWheel.y + rightWheel.y) / 2
                    if (midY <= avgWheelY) {
                        confidence += 0.2f
                    }
                }
            }
        }
        
        return minOf(confidence, 1.0f)
    }
}