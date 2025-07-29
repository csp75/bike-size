package com.bikesize

/**
 * Data class representing a detected circle (wheel).
 */
data class DetectedCircle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float = 1.0f
)

/**
 * Data class representing a detected line segment (frame tube).
 */
data class DetectedLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val length: Float,
    val angle: Float,
    val confidence: Float = 1.0f
)

/**
 * Data class representing geometric measurements.
 */
data class GeometryMeasurements(
    val wheelbasePixels: Float,
    val averageWheelDiameterPixels: Float,
    val perspectiveCorrectionFactor: Float = 1.0f
)

/**
 * Data class for detection results and confidence scores.
 */
data class DetectionResults(
    val wheelsFound: Int,
    val wheelPositions: List<DetectedCircle>,
    val frameTubesFound: Int,
    val frameLines: List<DetectedLine>,
    val measurements: GeometryMeasurements,
    val confidenceScores: ConfidenceScores
)

/**
 * Data class for confidence scores.
 */
data class ConfidenceScores(
    val wheelDetection: Float,
    val frameDetection: Float
)

/**
 * Configuration for detection algorithms.
 */
data class DetectionConfig(
    val houghCirclesDp: Double = 1.2,
    val houghCirclesMinDist: Double = 0.125, // fraction of image height
    val houghCirclesParam1: Double = 100.0,
    val houghCirclesParam2: Double = 30.0,
    val houghCirclesMinRadius: Double = 0.05, // fraction of image height
    val houghCirclesMaxRadius: Double = 0.33, // fraction of image height
    val minLineLength: Int = 20,
    val maxLineGap: Int = 10,
    val lineAngleTolerance: Double = 5.0
)