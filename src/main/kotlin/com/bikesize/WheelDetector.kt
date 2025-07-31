package com.bikesize

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import kotlin.math.*

/**
 * Detects bicycle wheels using OpenCV's HoughCircles algorithm.
 */
class WheelDetector(private val config: DetectionConfig = DetectionConfig()) {
    private val logger = LoggerFactory.getLogger(WheelDetector::class.java)

    /**
     * Detects wheels in the preprocessed image.
     * 
     * @param imageData The loaded and preprocessed image data
     * @return List of detected circles representing wheels
     */
    fun detectWheels(imageData: ImageLoader.ImageData): List<DetectedCircle> {
        logger.info("Starting wheel detection using HoughCircles")
        
        val circles = Mat()
        val image = imageData.preprocessed
        val imageHeight = imageData.height
        val imageWidth = imageData.width
        
        // Use adaptive scaling based on image dimensions and aspect ratio
        val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
        val scalingDimension = if (aspectRatio > 1.5) {
            // For wide images, use a combination of width and height for better scaling
            sqrt(imageWidth * imageHeight.toDouble()).toInt()
        } else {
            // For more square images, use height as before
            imageHeight
        }
        
        // Adjust param2 for wide images to be more sensitive
        val adaptiveParam2 = if (aspectRatio > 1.5) {
            config.houghCirclesParam2 * 0.8 // More sensitive for wide images
        } else {
            config.houghCirclesParam2
        }
        
        // Calculate dynamic parameters based on adaptive scaling
        val minDist = (scalingDimension * config.houghCirclesMinDist).toInt()
        val minRadius = (scalingDimension * config.houghCirclesMinRadius).toInt()
        val maxRadius = (scalingDimension * config.houghCirclesMaxRadius).toInt()
        
        logger.debug("HoughCircles parameters: dp=${config.houghCirclesDp}, minDist=$minDist, " +
                    "param1=${config.houghCirclesParam1}, param2=$adaptiveParam2, " +
                    "minRadius=$minRadius, maxRadius=$maxRadius, aspectRatio=$aspectRatio")

        // Apply HoughCircles detection
        Imgproc.HoughCircles(
            image,
            circles,
            Imgproc.HOUGH_GRADIENT,
            config.houghCirclesDp,
            minDist.toDouble(),
            config.houghCirclesParam1,
            adaptiveParam2,
            minRadius,
            maxRadius
        )

        val detectedCircles = mutableListOf<DetectedCircle>()
        
        // Process detected circles
        if (circles.cols() > 0) {
            for (i in 0 until circles.cols()) {
                val circle = circles.get(0, i)
                val x = circle[0].toFloat()
                val y = circle[1].toFloat()
                val radius = circle[2].toFloat()
                
                // Calculate confidence based on radius consistency and position
                val confidence = calculateCircleConfidence(x, y, radius, imageData)
                
                detectedCircles.add(DetectedCircle(x, y, radius, confidence))
                logger.debug("Detected circle: x=$x, y=$y, radius=$radius, confidence=$confidence")
            }
        }

        // Sort by confidence and validate results
        val sortedCircles = detectedCircles.sortedByDescending { it.confidence }
        
        logger.info("Detected ${sortedCircles.size} circles")
        
        // Filter to get the two most likely wheels
        val validatedWheels = validateWheelDetection(sortedCircles, imageData)
        
        logger.info("Validated ${validatedWheels.size} wheels")
        return validatedWheels
    }

    /**
     * Calculates confidence score for a detected circle.
     */
    private fun calculateCircleConfidence(x: Float, y: Float, radius: Float, imageData: ImageLoader.ImageData): Float {
        var confidence = 1.0f
        
        // Penalize circles too close to edges
        val margin = 50
        if (x < margin || y < margin || 
            x > imageData.width - margin || y > imageData.height - margin) {
            confidence *= 0.7f
        }
        
        // Favor circles in the lower portion of the image (where wheels typically are)
        val verticalPosition = y / imageData.height
        if (verticalPosition > 0.4f) {
            confidence *= 1.2f
        } else {
            confidence *= 0.8f
        }
        
        // Favor reasonable wheel sizes
        val expectedRadiusRange = (imageData.height * 0.1f)..(imageData.height * 0.25f)
        if (radius in expectedRadiusRange) {
            confidence *= 1.1f
        } else {
            confidence *= 0.9f
        }
        
        return minOf(confidence, 1.0f)
    }

    /**
     * Validates wheel detection results and filters to most likely wheels.
     */
    private fun validateWheelDetection(
        circles: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        if (circles.isEmpty()) {
            logger.warn("No circles detected")
            return emptyList()
        }

        if (circles.size == 1) {
            logger.warn("Only one circle detected, expected two wheels")
            return circles
        }

        if (circles.size == 2) {
            logger.info("Exactly two circles detected, validating as wheel pair")
            return validateWheelPair(circles[0], circles[1], imageData)
        }

        // More than 2 circles detected, find the best pair
        logger.info("Multiple circles detected (${circles.size}), finding best wheel pair")
        return findBestWheelPair(circles, imageData)
    }

    /**
     * Validates that two detected circles form a reasonable wheel pair.
     */
    private fun validateWheelPair(
        circle1: DetectedCircle,
        circle2: DetectedCircle,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        val distance = sqrt((circle1.x - circle2.x).pow(2) + (circle1.y - circle2.y).pow(2))
        val avgRadius = (circle1.radius + circle2.radius) / 2
        
        // Check if wheels are roughly at the same vertical level
        val verticalDifference = abs(circle1.y - circle2.y)
        val maxVerticalDifference = avgRadius * 0.5f
        
        if (verticalDifference > maxVerticalDifference) {
            logger.warn("Wheels not at similar vertical level (diff: $verticalDifference)")
        }
        
        // Check if distance between wheels is reasonable (typical wheelbase)
        val minWheelbase = imageData.width * 0.3f
        val maxWheelbase = imageData.width * 0.8f
        
        if (distance < minWheelbase || distance > maxWheelbase) {
            logger.warn("Wheelbase distance ($distance) outside expected range ($minWheelbase-$maxWheelbase)")
        }
        
        return listOf(circle1, circle2)
    }

    /**
     * Finds the best pair of wheels from multiple detected circles.
     */
    private fun findBestWheelPair(
        circles: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        var bestPair: Pair<DetectedCircle, DetectedCircle>? = null
        var bestScore = 0.0f
        
        for (i in circles.indices) {
            for (j in i + 1 until circles.size) {
                val circle1 = circles[i]
                val circle2 = circles[j]
                val score = calculatePairScore(circle1, circle2, imageData)
                
                if (score > bestScore) {
                    bestScore = score
                    bestPair = Pair(circle1, circle2)
                }
            }
        }
        
        return bestPair?.let { listOf(it.first, it.second) } ?: circles.take(2)
    }

    /**
     * Calculates a score for how likely two circles are to be a wheel pair.
     */
    private fun calculatePairScore(
        circle1: DetectedCircle,
        circle2: DetectedCircle,
        imageData: ImageLoader.ImageData
    ): Float {
        val distance = sqrt((circle1.x - circle2.x).pow(2) + (circle1.y - circle2.y).pow(2))
        val avgRadius = (circle1.radius + circle2.radius) / 2
        val radiusDifference = abs(circle1.radius - circle2.radius)
        val verticalDifference = abs(circle1.y - circle2.y)
        
        var score = (circle1.confidence + circle2.confidence) / 2
        
        // Prefer similar sized wheels
        val radiusConsistency = 1.0f - (radiusDifference / avgRadius)
        score *= radiusConsistency
        
        // Prefer wheels at similar vertical level
        val verticalConsistency = 1.0f - (verticalDifference / avgRadius)
        score *= verticalConsistency
        
        // Prefer reasonable wheelbase
        val expectedWheelbase = imageData.width * 0.5f
        val wheelbaseScore = 1.0f - abs(distance - expectedWheelbase) / expectedWheelbase
        score *= maxOf(wheelbaseScore, 0.1f)
        
        return score
    }
}