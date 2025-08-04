package com.bikesize

import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Creates visualizations with detected components overlaid on the original image.
 */
class Visualizer {
    private val logger = LoggerFactory.getLogger(Visualizer::class.java)

    /**
     * Creates an annotated image with detection results overlaid.
     * 
     * @param imageData Original image data
     * @param detectionResults All detection results
     * @param outputPath Path where to save the annotated image
     * @return Path to the saved annotated image
     */
    fun createAnnotatedImage(
        imageData: ImageLoader.ImageData,
        detectionResults: DetectionResults,
        outputPath: String
    ): String {
        logger.info("Creating annotated visualization")
        
        // Clone the original image for annotation
        val annotatedImage = imageData.original.clone()
        
        // Draw detected wheels
        drawWheels(annotatedImage, detectionResults.wheelPositions)
        
        // Draw detected frame lines
        drawFrameLines(annotatedImage, detectionResults.frameLines)
        
        // Add measurement labels
        addMeasurementLabels(annotatedImage, detectionResults)
        
        // Add confidence indicators
        addConfidenceIndicators(annotatedImage, detectionResults.confidenceScores)
        
        // Ensure output directory exists
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        
        // Save the annotated image
        val success = Imgcodecs.imwrite(outputPath, annotatedImage)
        if (!success) {
            throw RuntimeException("Failed to save annotated image to: $outputPath")
        }
        
        logger.info("Annotated image saved to: $outputPath")
        return outputPath
    }

    /**
     * Draws detected wheels as thick green circles.
     */
    private fun drawWheels(image: Mat, wheels: List<DetectedCircle>) {
        wheels.forEachIndexed { index, wheel ->
            val center = Point(wheel.x.toDouble(), wheel.y.toDouble())
            val radius = wheel.radius.toInt()
            
            // Choose color based on confidence
            val color = getConfidenceColor(wheel.confidence)
            
            // Draw the wheel circle with thick line
            Imgproc.circle(image, center, radius, color, 3)
            
            // Draw center point
            Imgproc.circle(image, center, 5, color, -1)
            
            // Add wheel label
            val labelText = "W${index + 1}"
            val labelPos = Point(wheel.x.toDouble() - 15, wheel.y.toDouble() - radius - 10)
            Imgproc.putText(
                image, labelText, labelPos,
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, color, 2
            )
            
            logger.debug("Drew wheel $index at (${wheel.x}, ${wheel.y}) with radius ${wheel.radius}")
        }
    }

    /**
     * Draws detected frame lines as blue lines.
     */
    private fun drawFrameLines(image: Mat, frameLines: List<DetectedLine>) {
        frameLines.forEachIndexed { index, line ->
            val pt1 = Point(line.x1.toDouble(), line.y1.toDouble())
            val pt2 = Point(line.x2.toDouble(), line.y2.toDouble())
            
            // Choose color based on confidence
            val color = getConfidenceColor(line.confidence, baseColor = Scalar(255.0, 100.0, 0.0)) // Blue base
            
            // Draw the line
            Imgproc.line(image, pt1, pt2, color, 2)
            
            // Add line number at midpoint
            val midX = (line.x1 + line.x2) / 2
            val midY = (line.y1 + line.y2) / 2
            val labelPos = Point(midX.toDouble(), midY.toDouble())
            
            Imgproc.putText(
                image, "${index + 1}", labelPos,
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1
            )
            
            logger.debug("Drew frame line $index from (${line.x1}, ${line.y1}) to (${line.x2}, ${line.y2})")
        }
    }

    /**
     * Adds measurement labels to the image.
     */
    private fun addMeasurementLabels(image: Mat, detectionResults: DetectionResults) {
        val measurements = detectionResults.measurements
        val textColor = Scalar(255.0, 255.0, 255.0) // White text
        val bgColor = Scalar(0.0, 0.0, 0.0) // Black background
        
        val labels = listOf(
            "Wheelbase: ${measurements.wheelbasePixels.toInt()} px",
            "Avg Wheel Diameter: ${measurements.averageWheelDiameterPixels.toInt()} px",
            "Wheels Found: ${detectionResults.wheelsFound}",
            "Frame Tubes Found: ${detectionResults.frameTubesFound}",
            "Perspective Factor: ${"%.2f".format(measurements.perspectiveCorrectionFactor)}"
        )
        
        val startY = 30
        val lineHeight = 25
        val padding = 5
        
        labels.forEachIndexed { index, label ->
            val y = startY + (index * lineHeight)
            val textSize = Imgproc.getTextSize(label, Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, 1, null)
            
            // Draw background rectangle
            val bgStart = Point(5.0, y - textSize.height - padding.toDouble())
            val bgEnd = Point(15.0 + textSize.width, y + padding.toDouble())
            Imgproc.rectangle(image, bgStart, bgEnd, bgColor, -1)
            
            // Draw text
            val textPos = Point(10.0, y.toDouble())
            Imgproc.putText(
                image, label, textPos,
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, textColor, 1
            )
        }
    }

    /**
     * Adds confidence indicators to the image.
     */
    private fun addConfidenceIndicators(image: Mat, confidenceScores: ConfidenceScores) {
        val height = image.rows()
        val width = image.cols()
        
        // Draw confidence bars in the bottom right
        val barWidth = 150
        val barHeight = 20
        val startX = width - barWidth - 20
        val startY = height - 80
        
        // Wheel detection confidence
        drawConfidenceBar(
            image, "Wheel Detection", confidenceScores.wheelDetection,
            startX, startY, barWidth, barHeight
        )
        
        // Frame detection confidence
        drawConfidenceBar(
            image, "Frame Detection", confidenceScores.frameDetection,
            startX, startY + 30, barWidth, barHeight
        )
    }

    /**
     * Draws a confidence bar indicator.
     */
    private fun drawConfidenceBar(
        image: Mat, label: String, confidence: Float,
        x: Int, y: Int, width: Int, height: Int
    ) {
        val bgColor = Scalar(50.0, 50.0, 50.0) // Dark gray background
        val fillColor = getConfidenceColor(confidence)
        val textColor = Scalar(255.0, 255.0, 255.0) // White text
        
        // Draw background
        val bgStart = Point(x.toDouble(), y.toDouble())
        val bgEnd = Point((x + width).toDouble(), (y + height).toDouble())
        Imgproc.rectangle(image, bgStart, bgEnd, bgColor, -1)
        
        // Draw fill based on confidence
        val fillWidth = (width * confidence).toInt()
        val fillEnd = Point((x + fillWidth).toDouble(), (y + height).toDouble())
        Imgproc.rectangle(image, bgStart, fillEnd, fillColor, -1)
        
        // Draw border
        Imgproc.rectangle(image, bgStart, bgEnd, Scalar(255.0, 255.0, 255.0), 1)
        
        // Draw label
        val labelPos = Point(x.toDouble(), (y - 5).toDouble())
        Imgproc.putText(
            image, "$label: ${"%.1f".format(confidence * 100)}%", labelPos,
            Imgproc.FONT_HERSHEY_SIMPLEX, 0.4, textColor, 1
        )
    }

    /**
     * Gets color based on confidence level.
     * High confidence = green, medium = yellow, low = red
     */
    private fun getConfidenceColor(
        confidence: Float,
        baseColor: Scalar = Scalar(0.0, 255.0, 0.0) // Green base
    ): Scalar {
        return when {
            confidence >= 0.8f -> Scalar(0.0, 255.0, 0.0) // Green (BGR format)
            confidence >= 0.5f -> Scalar(0.0, 255.0, 255.0) // Yellow
            else -> Scalar(0.0, 0.0, 255.0) // Red
        }
    }

    /**
     * Generates the output filename based on input filename with optional versioning.
     */
    fun generateOutputFilename(inputPath: String, outputDir: String, overwrite: Boolean = false): String {
        val inputFile = File(inputPath)
        val nameWithoutExt = inputFile.nameWithoutExtension
        return generateVersionedFilename(outputDir, "${nameWithoutExt}_detected", "jpg", overwrite)
    }

    /**
     * Generates a versioned filename that either overwrites or adds incrementing suffix.
     */
    private fun generateVersionedFilename(outputDir: String, baseName: String, extension: String, overwrite: Boolean): String {
        val baseFile = File(outputDir, "$baseName.$extension")
        
        if (overwrite || !baseFile.exists()) {
            return baseFile.absolutePath
        }
        
        // File exists and overwrite is false, find next available version
        var counter = 1
        var versionedFile: File
        do {
            versionedFile = File(outputDir, "$baseName-$counter.$extension")
            counter++
        } while (versionedFile.exists())
        
        return versionedFile.absolutePath
    }
}