package com.bikesize

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import kotlin.math.*

/**
 * Implements wheel detection using OpenCV's HoughCircles algorithm.
 */
class OpenCvWheelDetector(private val config: DetectionConfig = DetectionConfig()) : WheelDetector {
    private val logger = LoggerFactory.getLogger(OpenCvWheelDetector::class.java)

    /**
     * Detects wheels in the preprocessed image using OpenCV's HoughCircles.
     *
     * @param imageData The loaded and preprocessed image data
     * @param appConfig Application configuration including debug settings
     * @return List of detected circles representing wheels
     */
    override fun detectWheels(imageData: ImageLoader.ImageData, appConfig: BikeGeometryDetector.AppConfig): List<DetectedCircle> {
        logger.info("Starting wheel detection using HoughCircles with concentric circle analysis")

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
            config.houghCirclesParam2 * 0.9 // Slightly more sensitive for wide images
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

        // Analyze concentric circles to identify wheel components
        val wheelComponentCircles = analyzeConcentricCircles(sortedCircles, imageData)

        logger.info("Identified ${wheelComponentCircles.size} wheel component circles")

        // Save debug image with all detected circles if debug mode is enabled
        if (appConfig.debugMode) {
            saveDebugImage(wheelComponentCircles, imageData, appConfig)
        }

        // Filter to get the two most likely wheels
        val validatedWheels = validateWheelDetection(wheelComponentCircles, imageData)

        logger.info("Validated ${validatedWheels.size} wheels")
        return validatedWheels
    }

    /**
     * Analyzes detected circles to identify concentric patterns (rim and tire).
     */
    private fun analyzeConcentricCircles(
        circles: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        val wheelComponents = mutableListOf<DetectedCircle>()
        val processed = mutableSetOf<Int>()

        for (i in circles.indices) {
            if (i in processed) continue

            val circle1 = circles[i]
            var bestConcentricPair: Pair<DetectedCircle, DetectedCircle>? = null
            var bestConcentricScore = 0.0f

            // Look for concentric circles
            for (j in i + 1 until circles.size) {
                if (j in processed) continue

                val circle2 = circles[j]
                val concentricScore = calculateConcentricScore(circle1, circle2, imageData)

                if (concentricScore > bestConcentricScore && concentricScore > 0.7f) {
                    bestConcentricScore = concentricScore
                    val (outer, inner) = if (circle1.radius > circle2.radius) {
                        Pair(circle1, circle2)
                    } else {
                        Pair(circle2, circle1)
                    }
                    bestConcentricPair = Pair(outer, inner)
                }
            }

            if (bestConcentricPair != null) {
                // Found concentric circles - classify as tire (outer) and rim (inner)
                val (outer, inner) = bestConcentricPair
                val tireCircle = outer.copy(
                    component = WheelComponent.TIRE,
                    concentricPartner = inner
                )
                val rimCircle = inner.copy(
                    component = WheelComponent.RIM,
                    concentricPartner = outer
                )

                wheelComponents.add(tireCircle)
                wheelComponents.add(rimCircle)

                // Mark both circles as processed
                processed.add(circles.indexOf(outer))
                processed.add(circles.indexOf(inner))

                logger.debug("Found concentric pair: tire(${outer.x}, ${outer.y}, r=${outer.radius}) " +
                           "rim(${inner.x}, ${inner.y}, r=${inner.radius}) score=$bestConcentricScore")
            } else {
                // Single circle - classify as unknown wheel component
                wheelComponents.add(circle1.copy(component = WheelComponent.UNKNOWN))
                processed.add(i)
            }
        }

        return wheelComponents.sortedByDescending { it.confidence }
    }

    /**
     * Calculates how likely two circles are to be concentric (rim and tire).
     */
    private fun calculateConcentricScore(
        circle1: DetectedCircle,
        circle2: DetectedCircle,
        imageData: ImageLoader.ImageData
    ): Float {
        // Check center distance
        val centerDistance = sqrt((circle1.x - circle2.x).pow(2) + (circle1.y - circle2.y).pow(2))
        val avgRadius = (circle1.radius + circle2.radius) / 2

        // Centers should be very close for concentric circles
        val maxCenterDistance = avgRadius * config.concentricCircleToleranceRatio
        if (centerDistance > maxCenterDistance) {
            return 0.0f
        }

        // Check radius difference (should be meaningful but not too large)
        val radiusDiff = abs(circle1.radius - circle2.radius)
        val minRadiusDiff = imageData.height * config.minConcentricRadiusDiff
        val maxRadiusDiff = avgRadius * config.maxConcentricRadiusDiffRatio // Max 40% difference

        if (radiusDiff < minRadiusDiff || radiusDiff > maxRadiusDiff) {
            return 0.0f
        }

        // Calculate score based on concentricity quality
        val centerScore = 1.0f - (centerDistance / maxCenterDistance)
        val radiusScore = 1.0f - (radiusDiff / maxRadiusDiff)
        val confidenceScore = (circle1.confidence + circle2.confidence) / 2

        return (centerScore * 0.4f + radiusScore * 0.3f + confidenceScore * 0.3f).toFloat()
    }

    /**
     * Saves debug image with enhanced visualization for wheel components.
     */
    private fun saveDebugImage(
        circles: List<DetectedCircle>,
        imageData: ImageLoader.ImageData,
        appConfig: BikeGeometryDetector.AppConfig
    ) {
        val debugImage = imageData.original.clone()

        for (circle in circles) {
            val centerPoint = Point(circle.x.toDouble(), circle.y.toDouble())
            val radius = circle.radius.toInt()

            // Color-code by component type
            val circleColor = when (circle.component) {
                WheelComponent.TIRE -> Scalar(0.0, 255.0, 0.0)    // Green for tire
                WheelComponent.RIM -> Scalar(0.0, 0.0, 255.0)     // Blue for rim
                WheelComponent.UNKNOWN -> Scalar(255.0, 255.0, 0.0) // Yellow for unknown
            }

            // Draw circle outline
            Imgproc.circle(debugImage, centerPoint, radius, circleColor, 2)

            // Draw center point
            Imgproc.circle(debugImage, centerPoint, 3, Scalar(255.0, 0.0, 0.0), -1)

            // Draw component label
            val label = when (circle.component) {
                WheelComponent.TIRE -> "T"
                WheelComponent.RIM -> "R"
                WheelComponent.UNKNOWN -> "?"
            }
            Imgproc.putText(
                debugImage, label,
                Point(circle.x.toDouble() - 10, circle.y.toDouble() - circle.radius - 10),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, circleColor, 2
            )

            // Connect concentric partners with a line
            circle.concentricPartner?.let { partner ->
                val partnerPoint = Point(partner.x.toDouble(), partner.y.toDouble())
                Imgproc.line(debugImage, centerPoint, partnerPoint, Scalar(255.0, 0.0, 255.0), 1)
            }
        }

        val debugPath = FileUtils.generateDebugFilename(
            imageData.filePath, appConfig.outputPath, "wheel_detection", "jpg", appConfig.overwrite
        )
        if (Imgcodecs.imwrite(debugPath, debugImage)) {
            logger.info("Debug: Saved enhanced wheel detection image with ${circles.size} circles to: $debugPath")
        }
    }

    /**
     * Calculates a confidence score for a detected circle based on its position and size within the image.
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

        // Group by wheel (tire/rim pairs or single unknown circles)
        val wheelGroups = groupCirclesByWheel(circles)

        logger.debug("Found ${wheelGroups.size} wheel groups")

        if (wheelGroups.size == 1) {
            logger.warn("Only one wheel group detected, expected two wheels")
            return wheelGroups.first()
        }

        if (wheelGroups.size == 2) {
            logger.info("Exactly two wheel groups detected, validating as wheel pair")
            val wheel1 = wheelGroups[0]
            val wheel2 = wheelGroups[1]
            return validateWheelPair(wheel1, wheel2, imageData)
        }

        // More than 2 wheel groups detected, find the best pair
        logger.info("Multiple wheel groups detected (${wheelGroups.size}), finding best wheel pair")
        return findBestWheelPair(wheelGroups, imageData)
    }

    /**
     * Groups detected circles by wheel (tire/rim pairs or individual circles).
     */
    private fun groupCirclesByWheel(circles: List<DetectedCircle>): List<List<DetectedCircle>> {
        val wheelGroups = mutableListOf<List<DetectedCircle>>()
        val processed = mutableSetOf<DetectedCircle>()

        for (circle in circles) {
            if (circle in processed) continue

            val wheelGroup = mutableListOf<DetectedCircle>()
            wheelGroup.add(circle)
            processed.add(circle)

            // If this circle has a concentric partner, add it to the group
            circle.concentricPartner?.let { partner ->
                if (partner in circles && partner !in processed) {
                    wheelGroup.add(partner)
                    processed.add(partner)
                }
            }

            wheelGroups.add(wheelGroup)
        }

        return wheelGroups
    }

    /**
     * Validates that two detected wheel groups form a reasonable wheel pair.
     */
    private fun validateWheelPair(
        wheel1: List<DetectedCircle>,
        wheel2: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        // Get representative circle for each wheel (prefer tire, fallback to largest)
        val circle1 = wheel1.find { it.component == WheelComponent.TIRE }
                     ?: wheel1.maxByOrNull { it.radius }
                     ?: return emptyList()

        val circle2 = wheel2.find { it.component == WheelComponent.TIRE }
                     ?: wheel2.maxByOrNull { it.radius }
                     ?: return emptyList()

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

        // Return all circles from both wheels
        return wheel1 + wheel2
    }

    /**
     * Finds the best pair of wheels from multiple detected wheel groups.
     */
    private fun findBestWheelPair(
        wheelGroups: List<List<DetectedCircle>>,
        imageData: ImageLoader.ImageData
    ): List<DetectedCircle> {
        var bestPair: Pair<List<DetectedCircle>, List<DetectedCircle>>? = null
        var bestScore = 0.0f

        for (i in wheelGroups.indices) {
            for (j in i + 1 until wheelGroups.size) {
                val wheel1 = wheelGroups[i]
                val wheel2 = wheelGroups[j]
                val score = calculateWheelPairScore(wheel1, wheel2, imageData)

                if (score > bestScore) {
                    bestScore = score
                    bestPair = Pair(wheel1, wheel2)
                }
            }
        }

        return bestPair?.let { it.first + it.second } ?: wheelGroups.take(2).flatten()
    }

    /**
     * Calculates a score for how likely two wheel groups are to be a wheel pair.
     */
    private fun calculateWheelPairScore(
        wheel1: List<DetectedCircle>,
        wheel2: List<DetectedCircle>,
        imageData: ImageLoader.ImageData
    ): Float {
        // Get representative circles
        val circle1 = wheel1.find { it.component == WheelComponent.TIRE }
                     ?: wheel1.maxByOrNull { it.radius }
                     ?: return 0.0f

        val circle2 = wheel2.find { it.component == WheelComponent.TIRE }
                     ?: wheel2.maxByOrNull { it.radius }
                     ?: return 0.0f

        return calculatePairScore(circle1, circle2, imageData)
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

