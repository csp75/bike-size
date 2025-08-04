package com.bikesize

/**
 * Enum for wheel component types.
 */
enum class WheelComponent {
    TIRE,           // Outer circle (tire)
    RIM,            // Inner circle (rim)  
    UNKNOWN         // Unclassified circle
}

/**
 * Enum for bicycle frame component types.
 */
enum class FrameComponent {
    SEAT_STAY_LEFT,     // Left seat stay
    SEAT_STAY_RIGHT,    // Right seat stay
    CHAIN_STAY_LEFT,    // Left chain stay  
    CHAIN_STAY_RIGHT,   // Right chain stay
    SEAT_TUBE,          // Seat tube
    TOP_TUBE,           // Top tube
    DOWN_TUBE,          // Down tube
    HEAD_TUBE,          // Head tube
    FORK,               // Fork (can be slightly bent)
    SEATPOST,           // Seatpost (extension of seat tube)
    UNKNOWN             // Unclassified frame line
}

/**
 * Data class representing a detected circle (wheel).
 */
data class DetectedCircle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val confidence: Float = 1.0f,
    val component: WheelComponent = WheelComponent.UNKNOWN,
    val concentricPartner: DetectedCircle? = null // Reference to concentric circle partner
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
    val confidence: Float = 1.0f,
    val component: FrameComponent = FrameComponent.UNKNOWN,
    val geometryScore: Float = 0.0f  // Score based on bicycle geometry rules
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
    val houghCirclesMinDist: Double = 0.2, // fraction of image height - increased to reduce overlapping detections
    val houghCirclesParam1: Double = 100.0,
    val houghCirclesParam2: Double = 50.0, // increased from 30 to reduce false positives
    val houghCirclesMinRadius: Double = 0.08, // fraction of image height - increased minimum
    val houghCirclesMaxRadius: Double = 0.3, // fraction of image height - slightly reduced maximum
    val minLineLength: Int = 20,
    val maxLineGap: Int = 10,
    val lineAngleTolerance: Double = 5.0,
    // New parameters for concentric circle detection
    val concentricCircleToleranceRatio: Double = 0.15, // tolerance for rim/tire radius difference
    val minConcentricRadiusDiff: Double = 0.02, // minimum radius difference for concentric detection
    val maxConcentricRadiusDiffRatio: Double = 0.4, // maximum radius difference ratio for concentric detection
    // New parameters for bicycle frame detection
    val frameComponentLengthThreshold: Double = 0.7, // threshold for matching component lengths
    val frameComponentAngleThreshold: Double = 0.6, // threshold for matching component angles (in radians)
    val frameMinComponentLength: Double = 0.05, // minimum component length as fraction of image diagonal
    val frameMaxHeadTubeRatio: Double = 0.3 // head tube should be significantly shorter than other tubes
)