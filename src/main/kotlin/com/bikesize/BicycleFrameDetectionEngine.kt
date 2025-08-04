package com.bikesize

import org.slf4j.LoggerFactory
import kotlin.math.*

/**
 * Engine for detecting and identifying bicycle frame components using bicycle geometry knowledge.
 * 
 * Implements rules for bicycle frame structure:
 * - Component relationships (triangles, quadrilaterals)
 * - Length matching requirements
 * - Angle matching requirements  
 * - Position and connection validation
 */
class BicycleFrameDetectionEngine(private val config: DetectionConfig) {
    private val logger = LoggerFactory.getLogger(BicycleFrameDetectionEngine::class.java)

    /**
     * Identifies frame components from detected lines using bicycle geometry knowledge.
     */
    fun identifyFrameComponents(
        detectedLines: List<DetectedLine>,
        imageData: ImageLoader.ImageData,
        wheelPositions: List<DetectedCircle>
    ): List<DetectedLine> {
        if (detectedLines.isEmpty()) {
            logger.info("No lines detected to analyze for frame components")
            return emptyList()
        }

        if (wheelPositions.size < 2) {
            logger.warn("Cannot identify frame components without at least 2 wheels detected")
            return enhanceWithBasicGeometry(detectedLines, imageData)
        }

        logger.info("Analyzing ${detectedLines.size} lines for bicycle frame components")

        // Sort wheels left to right
        val sortedWheels = wheelPositions.sortedBy { it.x }
        val rearWheel = sortedWheels[0]
        val frontWheel = sortedWheels[1]

        // Filter lines by bicycle frame constraints
        val candidateLines = filterByCycleConstraints(detectedLines, imageData, rearWheel, frontWheel)
        
        // Group lines and identify components
        val identifiedComponents = identifyComponentsByGeometry(candidateLines, imageData, rearWheel, frontWheel)
        
        // Calculate geometry scores
        val scoredComponents = calculateGeometryScores(identifiedComponents, imageData, rearWheel, frontWheel)
        
        logger.info("Identified ${scoredComponents.size} frame components with bicycle geometry validation")
        return scoredComponents.sortedByDescending { it.confidence + it.geometryScore }
    }

    /**
     * Filters lines by basic bicycle frame constraints.
     */
    private fun filterByCycleConstraints(
        lines: List<DetectedLine>,
        imageData: ImageLoader.ImageData,
        rearWheel: DetectedCircle,
        frontWheel: DetectedCircle
    ): List<DetectedLine> {
        val imageDiagonal = sqrt(imageData.width.toFloat().pow(2) + imageData.height.toFloat().pow(2))
        val minComponentLength = imageDiagonal * config.frameMinComponentLength.toFloat()
        val wheelbaseLength = abs(frontWheel.x - rearWheel.x)
        
        return lines.filter { line ->
            // Must be long enough to be a meaningful frame component
            line.length >= minComponentLength &&
            // Must not be too horizontal (likely ground) or too vertical (likely post/tree)
            abs(line.angle % 180) > 10 && abs(line.angle % 180) < 170 &&
            // Must be positioned somewhat between the wheels
            isLineInBikeArea(line, rearWheel, frontWheel) &&
            // Must not be excessively long (likely noise or background)
            line.length <= wheelbaseLength * 1.5f
        }
    }

    /**
     * Checks if a line is positioned in the bicycle area (between and around wheels).
     */
    private fun isLineInBikeArea(line: DetectedLine, rearWheel: DetectedCircle, frontWheel: DetectedCircle): Boolean {
        val midX = (line.x1 + line.x2) / 2
        val midY = (line.y1 + line.y2) / 2
        
        // Expand the bike area slightly beyond wheels
        val leftBound = rearWheel.x - rearWheel.radius
        val rightBound = frontWheel.x + frontWheel.radius
        val topBound = minOf(rearWheel.y, frontWheel.y) - maxOf(rearWheel.radius, frontWheel.radius) * 1.5f
        val bottomBound = maxOf(rearWheel.y, frontWheel.y) + maxOf(rearWheel.radius, frontWheel.radius) * 0.5f
        
        return midX >= leftBound && midX <= rightBound && midY >= topBound && midY <= bottomBound
    }

    /**
     * Identifies frame components by analyzing their geometry and relationships.
     */
    private fun identifyComponentsByGeometry(
        lines: List<DetectedLine>,
        imageData: ImageLoader.ImageData,
        rearWheel: DetectedCircle,
        frontWheel: DetectedCircle
    ): List<DetectedLine> {
        val identifiedLines = mutableListOf<DetectedLine>()
        
        // Define reference points
        val rearAxle = Point2D(rearWheel.x, rearWheel.y)
        val frontAxle = Point2D(frontWheel.x, frontWheel.y)
        val avgWheelY = (rearWheel.y + frontWheel.y) / 2
        
        for (line in lines) {
            val component = classifyLineComponent(line, rearAxle, frontAxle, avgWheelY, lines)
            if (component != FrameComponent.UNKNOWN) {
                identifiedLines.add(line.copy(component = component))
            } else {
                // Keep as unknown but still include if it meets basic criteria
                identifiedLines.add(line)
            }
        }
        
        return identifiedLines
    }

    /**
     * Classifies a line as a specific frame component based on its geometry.
     */
    private fun classifyLineComponent(
        line: DetectedLine,
        rearAxle: Point2D,
        frontAxle: Point2D,
        avgWheelY: Float,
        allLines: List<DetectedLine>
    ): FrameComponent {
        val startPoint = Point2D(line.x1, line.y1)
        val endPoint = Point2D(line.x2, line.y2)
        val midPoint = Point2D((line.x1 + line.x2) / 2, (line.y1 + line.y2) / 2)
        
        // Classify based on position and angle
        when {
            // Seat tube: generally vertical-ish, positioned rear-center of bike
            isNearVertical(line.angle) && midPoint.x < (rearAxle.x + frontAxle.x) / 2 -> {
                return FrameComponent.SEAT_TUBE
            }
            
            // Head tube: short and steep, positioned front of bike
            isNearVertical(line.angle) && midPoint.x > (rearAxle.x + frontAxle.x) / 2 && 
            line.length < avgLineLength(allLines) * config.frameMaxHeadTubeRatio -> {
                return FrameComponent.HEAD_TUBE
            }
            
            // Top tube: horizontal-ish, positioned above wheel level
            isNearHorizontal(line.angle) && midPoint.y < avgWheelY -> {
                return FrameComponent.TOP_TUBE
            }
            
            // Down tube: angled down-forward, connecting lower areas
            isDiagonalDownForward(line.angle) && midPoint.y > avgWheelY - 50 -> {
                return FrameComponent.DOWN_TUBE
            }
            
            // Chain stay: lower horizontal-ish, connecting rear axle area
            isNearHorizontal(line.angle) && midPoint.y >= avgWheelY && 
            distanceToPoint(startPoint, rearAxle) < 100 || distanceToPoint(endPoint, rearAxle) < 100 -> {
                return if (midPoint.y > avgWheelY) FrameComponent.CHAIN_STAY_LEFT else FrameComponent.CHAIN_STAY_RIGHT
            }
            
            // Seat stay: angled up-back, connecting rear axle to seat area
            isDiagonalUpBack(line.angle) && 
            (distanceToPoint(startPoint, rearAxle) < 100 || distanceToPoint(endPoint, rearAxle) < 100) -> {
                return if (midPoint.y < avgWheelY) FrameComponent.SEAT_STAY_LEFT else FrameComponent.SEAT_STAY_RIGHT
            }
            
            // Fork: angled forward from front axle
            isDiagonalForward(line.angle) && midPoint.x > frontAxle.x -> {
                return FrameComponent.FORK
            }
            
            else -> return FrameComponent.UNKNOWN
        }
    }

    /**
     * Calculates geometry scores based on bicycle frame rules.
     */
    private fun calculateGeometryScores(
        lines: List<DetectedLine>,
        imageData: ImageLoader.ImageData,
        rearWheel: DetectedCircle,
        frontWheel: DetectedCircle
    ): List<DetectedLine> {
        return lines.map { line ->
            val geometryScore = calculateIndividualGeometryScore(line, lines, rearWheel, frontWheel)
            line.copy(geometryScore = geometryScore)
        }
    }

    /**
     * Calculates geometry score for individual line based on bicycle frame rules.
     */
    private fun calculateIndividualGeometryScore(
        line: DetectedLine,
        allLines: List<DetectedLine>,
        rearWheel: DetectedCircle,
        frontWheel: DetectedCircle
    ): Float {
        var score = 0.0f
        
        // Score based on component identification
        score += when (line.component) {
            FrameComponent.SEAT_TUBE, FrameComponent.TOP_TUBE, FrameComponent.DOWN_TUBE -> 0.3f
            FrameComponent.SEAT_STAY_LEFT, FrameComponent.SEAT_STAY_RIGHT -> 0.25f
            FrameComponent.CHAIN_STAY_LEFT, FrameComponent.CHAIN_STAY_RIGHT -> 0.25f
            FrameComponent.HEAD_TUBE -> 0.2f
            FrameComponent.FORK -> 0.15f
            else -> 0.0f
        }
        
        // Score based on length relationships with other components
        score += calculateLengthMatchingScore(line, allLines)
        
        // Score based on angle relationships with other components
        score += calculateAngleMatchingScore(line, allLines)
        
        // Score based on connection to wheels/bike structure
        score += calculateConnectionScore(line, rearWheel, frontWheel)
        
        return minOf(score, 1.0f)
    }

    /**
     * Basic geometry enhancement when wheels are not available.
     */
    private fun enhanceWithBasicGeometry(lines: List<DetectedLine>, imageData: ImageLoader.ImageData): List<DetectedLine> {
        // Apply basic geometric filtering without wheel information
        val imageDiagonal = sqrt(imageData.width.toFloat().pow(2) + imageData.height.toFloat().pow(2))
        val minLength = imageDiagonal * config.frameMinComponentLength.toFloat()
        
        return lines.filter { line ->
            line.length >= minLength &&
            abs(line.angle % 180) > 10 && abs(line.angle % 180) < 170 &&
            line.confidence > 0.3f
        }.sortedByDescending { it.confidence }
    }

    // Helper functions for angle classification
    private fun isNearVertical(angle: Float): Boolean = abs(abs(angle % 180) - 90) <= 30
    private fun isNearHorizontal(angle: Float): Boolean = abs(angle % 180) <= 30 || abs(angle % 180) >= 150
    private fun isDiagonalDownForward(angle: Float): Boolean = angle in -60.0..-10.0 || angle in 120.0..170.0
    private fun isDiagonalUpBack(angle: Float): Boolean = angle in 10.0..60.0 || angle in -170.0..-120.0
    private fun isDiagonalForward(angle: Float): Boolean = abs(angle % 180) in 20.0..70.0

    // Helper functions for geometric calculations
    private fun avgLineLength(lines: List<DetectedLine>): Float = 
        if (lines.isNotEmpty()) lines.map { it.length }.average().toFloat() else 0f
    
    private fun distanceToPoint(point1: Point2D, point2: Point2D): Float =
        sqrt((point1.x - point2.x).pow(2) + (point1.y - point2.y).pow(2))

    private fun calculateLengthMatchingScore(line: DetectedLine, allLines: List<DetectedLine>): Float {
        // Implementation for length matching score based on bicycle geometry rules
        // Similar tubes should have similar lengths
        val similarComponents = allLines.filter { otherLine ->
            when (line.component) {
                FrameComponent.SEAT_STAY_LEFT, FrameComponent.SEAT_STAY_RIGHT ->
                    otherLine.component in listOf(FrameComponent.TOP_TUBE, FrameComponent.DOWN_TUBE)
                FrameComponent.TOP_TUBE, FrameComponent.DOWN_TUBE ->
                    otherLine.component in listOf(FrameComponent.SEAT_STAY_LEFT, FrameComponent.SEAT_STAY_RIGHT)
                else -> false
            }
        }
        
        if (similarComponents.isEmpty()) return 0.0f
        
        val avgSimilarLength = similarComponents.map { it.length }.average().toFloat()
        val lengthDiff = abs(line.length - avgSimilarLength) / avgSimilarLength
        
        return if (lengthDiff <= config.frameComponentLengthThreshold) 0.2f else 0.0f
    }

    private fun calculateAngleMatchingScore(line: DetectedLine, allLines: List<DetectedLine>): Float {
        // Implementation for angle matching score based on bicycle geometry rules
        val matchingAngles = when (line.component) {
            FrameComponent.SEAT_STAY_LEFT, FrameComponent.SEAT_STAY_RIGHT -> 
                allLines.filter { it.component == FrameComponent.DOWN_TUBE }
            FrameComponent.CHAIN_STAY_LEFT, FrameComponent.CHAIN_STAY_RIGHT ->
                allLines.filter { it.component == FrameComponent.TOP_TUBE }
            FrameComponent.SEAT_TUBE ->
                allLines.filter { it.component in listOf(FrameComponent.HEAD_TUBE, FrameComponent.FORK) }
            else -> emptyList()
        }
        
        if (matchingAngles.isEmpty()) return 0.0f
        
        val angleMatches = matchingAngles.any { otherLine ->
            val angleDiff = abs(line.angle - otherLine.angle)
            val minAngleDiff = minOf(angleDiff, 180 - angleDiff)
            minAngleDiff <= config.frameComponentAngleThreshold * 180 / PI
        }
        
        return if (angleMatches) 0.15f else 0.0f
    }

    private fun calculateConnectionScore(line: DetectedLine, rearWheel: DetectedCircle, frontWheel: DetectedCircle): Float {
        val startPoint = Point2D(line.x1, line.y1)
        val endPoint = Point2D(line.x2, line.y2)
        val rearAxle = Point2D(rearWheel.x, rearWheel.y)
        val frontAxle = Point2D(frontWheel.x, frontWheel.y)
        
        val connectionThreshold = maxOf(rearWheel.radius, frontWheel.radius) * 1.2f
        
        return when (line.component) {
            FrameComponent.CHAIN_STAY_LEFT, FrameComponent.CHAIN_STAY_RIGHT,
            FrameComponent.SEAT_STAY_LEFT, FrameComponent.SEAT_STAY_RIGHT -> {
                if (distanceToPoint(startPoint, rearAxle) <= connectionThreshold ||
                    distanceToPoint(endPoint, rearAxle) <= connectionThreshold) 0.1f else 0.0f
            }
            FrameComponent.FORK -> {
                if (distanceToPoint(startPoint, frontAxle) <= connectionThreshold ||
                    distanceToPoint(endPoint, frontAxle) <= connectionThreshold) 0.1f else 0.0f
            }
            else -> 0.05f // Small bonus for being positioned correctly
        }
    }

    /**
     * Simple 2D point class for calculations.
     */
    private data class Point2D(val x: Float, val y: Float)
}