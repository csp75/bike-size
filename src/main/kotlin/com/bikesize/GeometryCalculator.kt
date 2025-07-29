package com.bikesize

import org.slf4j.LoggerFactory
import kotlin.math.*

/**
 * Calculates geometric measurements from detected wheels and frame components.
 */
class GeometryCalculator {
    private val logger = LoggerFactory.getLogger(GeometryCalculator::class.java)

    /**
     * Calculates basic geometric measurements from detected components.
     * 
     * @param wheels Detected wheel positions
     * @param frameLines Detected frame line segments
     * @param imageData Image dimensions and metadata
     * @return GeometryMeasurements containing calculated values
     */
    fun calculateMeasurements(
        wheels: List<DetectedCircle>,
        frameLines: List<DetectedLine>,
        imageData: ImageLoader.ImageData
    ): GeometryMeasurements {
        logger.info("Calculating geometry measurements")
        
        val wheelbasePixels = calculateWheelbase(wheels)
        val averageWheelDiameter = calculateAverageWheelDiameter(wheels)
        val perspectiveFactor = calculatePerspectiveCorrectionFactor(wheels, imageData)
        
        logger.info("Wheelbase: $wheelbasePixels pixels")
        logger.info("Average wheel diameter: $averageWheelDiameter pixels")
        logger.info("Perspective correction factor: $perspectiveFactor")
        
        return GeometryMeasurements(
            wheelbasePixels = wheelbasePixels,
            averageWheelDiameterPixels = averageWheelDiameter,
            perspectiveCorrectionFactor = perspectiveFactor
        )
    }

    /**
     * Calculates the wheelbase (distance between wheel centers).
     */
    private fun calculateWheelbase(wheels: List<DetectedCircle>): Float {
        if (wheels.size < 2) {
            logger.warn("Cannot calculate wheelbase with fewer than 2 wheels")
            return 0.0f
        }
        
        // Find the two wheels that are furthest apart horizontally
        val leftWheel = wheels.minByOrNull { it.x } ?: return 0.0f
        val rightWheel = wheels.maxByOrNull { it.x } ?: return 0.0f
        
        val distance = sqrt((rightWheel.x - leftWheel.x).pow(2) + (rightWheel.y - leftWheel.y).pow(2))
        
        logger.debug("Wheelbase calculation: left wheel at (${leftWheel.x}, ${leftWheel.y}), " +
                    "right wheel at (${rightWheel.x}, ${rightWheel.y}), distance: $distance")
        
        return distance
    }

    /**
     * Calculates the average wheel diameter from detected wheels.
     */
    private fun calculateAverageWheelDiameter(wheels: List<DetectedCircle>): Float {
        if (wheels.isEmpty()) {
            logger.warn("Cannot calculate wheel diameter with no wheels detected")
            return 0.0f
        }
        
        val averageDiameter = wheels.map { it.radius * 2 }.average().toFloat()
        
        logger.debug("Wheel diameters: ${wheels.map { it.radius * 2 }}, average: $averageDiameter")
        
        return averageDiameter
    }

    /**
     * Calculates perspective correction factor based on wheel ellipse eccentricity.
     * For now, this is a simplified calculation assuming circular wheels.
     */
    private fun calculatePerspectiveCorrectionFactor(
        wheels: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): Float {
        if (wheels.size < 2) {
            return 1.0f
        }
        
        // Simple perspective correction based on vertical alignment of wheels
        val leftWheel = wheels.minByOrNull { it.x }
        val rightWheel = wheels.maxByOrNull { it.x }
        
        if (leftWheel != null && rightWheel != null) {
            val verticalDifference = abs(leftWheel.y - rightWheel.y)
            val horizontalDistance = abs(rightWheel.x - leftWheel.x)
            
            if (horizontalDistance > 0) {
                val perspectiveAngle = atan(verticalDifference / horizontalDistance)
                val correctionFactor = cos(perspectiveAngle)
                
                logger.debug("Perspective angle: ${perspectiveAngle * 180 / PI} degrees, " +
                            "correction factor: $correctionFactor")
                
                return correctionFactor.toFloat()
            }
        }
        
        return 1.0f
    }

    /**
     * Identifies the main frame triangle from detected lines.
     * This is a simplified version that looks for the three longest lines that could form a triangle.
     */
    fun identifyFrameTriangle(frameLines: List<DetectedLine>): List<DetectedLine> {
        if (frameLines.size < 3) {
            logger.warn("Not enough frame lines to identify triangle (need at least 3, have ${frameLines.size})")
            return frameLines
        }
        
        // Sort by length and take the three longest lines
        val sortedLines = frameLines.sortedByDescending { it.length }
        val topThree = sortedLines.take(3)
        
        // Validate that these lines could form a triangle by checking angles
        val triangleLines = validateTriangleLines(topThree)
        
        logger.info("Identified ${triangleLines.size} lines as potential frame triangle")
        return triangleLines
    }

    /**
     * Validates that three lines could form a reasonable bicycle frame triangle.
     */
    private fun validateTriangleLines(lines: List<DetectedLine>): List<DetectedLine> {
        if (lines.size != 3) return lines
        
        val angles = lines.map { it.angle }
        val angleDifferences = mutableListOf<Float>()
        
        for (i in angles.indices) {
            for (j in i + 1 until angles.size) {
                val diff = abs(angles[i] - angles[j])
                angleDifferences.add(minOf(diff, 180 - diff))
            }
        }
        
        // Check if we have reasonably different angles (not all parallel)
        val hasVariedAngles = angleDifferences.any { it > 15 }
        
        if (hasVariedAngles) {
            logger.debug("Triangle validation passed: varied angles found")
            return lines
        } else {
            logger.warn("Triangle validation failed: all lines appear parallel")
            return lines // Return them anyway for now
        }
    }

    /**
     * Calculates confidence scores for the overall detection.
     */
    fun calculateConfidenceScores(
        wheels: List<DetectedCircle>,
        frameLines: List<DetectedLine>,
        imageData: ImageLoader.ImageData
    ): ConfidenceScores {
        val wheelConfidence = calculateWheelDetectionConfidence(wheels)
        val frameConfidence = calculateFrameDetectionConfidence(frameLines, imageData)
        
        logger.info("Confidence scores - Wheels: $wheelConfidence, Frame: $frameConfidence")
        
        return ConfidenceScores(
            wheelDetection = wheelConfidence,
            frameDetection = frameConfidence
        )
    }

    /**
     * Calculates confidence score for wheel detection.
     */
    private fun calculateWheelDetectionConfidence(wheels: List<DetectedCircle>): Float {
        when (wheels.size) {
            0 -> return 0.0f
            1 -> return 0.5f
            2 -> {
                val avgConfidence = wheels.map { it.confidence }.average().toFloat()
                val radiusConsistency = calculateRadiusConsistency(wheels)
                return (avgConfidence + radiusConsistency) / 2
            }
            else -> {
                // More than 2 wheels detected - lower confidence due to uncertainty
                val avgConfidence = wheels.map { it.confidence }.average().toFloat()
                return avgConfidence * 0.8f
            }
        }
    }

    /**
     * Calculates how consistent the wheel radii are.
     */
    private fun calculateRadiusConsistency(wheels: List<DetectedCircle>): Float {
        if (wheels.size < 2) return 1.0f
        
        val radii = wheels.map { it.radius }
        val avgRadius = radii.average()
        val maxDeviation = radii.map { abs(it - avgRadius) }.maxOrNull() ?: 0.0
        
        val consistency = 1.0f - (maxDeviation / avgRadius).toFloat()
        return maxOf(consistency, 0.0f)
    }

    /**
     * Calculates confidence score for frame detection.
     */
    private fun calculateFrameDetectionConfidence(frameLines: List<DetectedLine>, imageData: ImageLoader.ImageData): Float {
        if (frameLines.isEmpty()) return 0.0f
        
        val avgConfidence = frameLines.map { it.confidence }.average().toFloat()
        val lengthScore = calculateFrameLengthScore(frameLines, imageData)
        val varietyScore = calculateFrameVarietyScore(frameLines)
        
        return (avgConfidence + lengthScore + varietyScore) / 3
    }

    /**
     * Calculates a score based on the lengths of detected frame lines.
     */
    private fun calculateFrameLengthScore(frameLines: List<DetectedLine>, imageData: ImageLoader.ImageData): Float {
        val maxPossibleLength = sqrt(imageData.width.toFloat().pow(2) + imageData.height.toFloat().pow(2))
        val avgLength = frameLines.map { it.length }.average().toFloat()
        
        return minOf(avgLength / (maxPossibleLength * 0.3f), 1.0f)
    }

    /**
     * Calculates a score based on the variety of angles in frame lines.
     */
    private fun calculateFrameVarietyScore(frameLines: List<DetectedLine>): Float {
        if (frameLines.size < 2) return 0.5f
        
        val angles = frameLines.map { it.angle }
        val angleRanges = mutableSetOf<Int>()
        
        angles.forEach { angle ->
            val normalizedAngle = ((angle % 180 + 180) % 180).toInt()
            val rangeGroup = normalizedAngle / 30 // Group into 30-degree ranges
            angleRanges.add(rangeGroup)
        }
        
        // More variety in angles suggests better frame detection
        return minOf(angleRanges.size / 3.0f, 1.0f)
    }
}