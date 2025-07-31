package com.bikesize

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nu.pattern.OpenCV
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.abs
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * Main application for bike geometry detection.
 */
fun main(args: Array<String>) {
    val app = BikeGeometryDetector()
    app.run(args)
}

class BikeGeometryDetector {
    private val logger = LoggerFactory.getLogger(BikeGeometryDetector::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Main application entry point.
     */
    fun run(args: Array<String>) {
        logger.info("Starting Bike Geometry Detector")
        
        try {
            // Initialize OpenCV
            initializeOpenCV()
            
            // Parse command line arguments
            val config = parseArguments(args)
            
            // Process the image
            val processingTime = measureTimeMillis {
                processImage(config)
            }
            
            logger.info("Processing completed in ${processingTime}ms")
            
        } catch (e: Exception) {
            logger.error("Application failed: ${e.message}", e)
            exitProcess(1)
        }
    }

    /**
     * Initializes OpenCV library.
     */
    private fun initializeOpenCV() {
        try {
            OpenCV.loadShared()
            logger.info("OpenCV initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize OpenCV: ${e.message}", e)
            throw RuntimeException("OpenCV initialization failed", e)
        }
    }

    /**
     * Parses command line arguments.
     */
    private fun parseArguments(args: Array<String>): AppConfig {
        var inputPath: String? = null
        var outputPath: String? = null
        var debugMode = false
        
        // Check if first argument is a direct path/URL (doesn't start with --)
        if (args.isNotEmpty() && !args[0].startsWith("--")) {
            inputPath = args[0]
            // Parse remaining arguments starting from index 1
            val parseResult = parseRemainingArguments(args, 1)
            outputPath = parseResult.first
            debugMode = parseResult.second
        } else {
            // Original parsing logic for --input format
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--input" -> {
                        if (i + 1 < args.size) {
                            inputPath = args[++i]
                        } else {
                            throw IllegalArgumentException("--input requires a value")
                        }
                    }
                    "--output" -> {
                        if (i + 1 < args.size) {
                            outputPath = args[++i]
                        } else {
                            throw IllegalArgumentException("--output requires a value")
                        }
                    }
                    "--debug" -> {
                        debugMode = true
                    }
                    "--help", "-h" -> {
                        printUsage()
                        exitProcess(0)
                    }
                    else -> {
                        logger.warn("Unknown argument: ${args[i]}")
                    }
                }
                i++
            }
        }
        
        if (inputPath == null) {
            logger.error("Input path is required")
            printUsage()
            exitProcess(1)
        }
        
        val outputDir = outputPath ?: "./results"
        
        return AppConfig(
            inputPath = inputPath,
            outputPath = outputDir,
            debugMode = debugMode
        )
    }

    /**
     * Parses remaining arguments after the first path/URL argument.
     */
    private fun parseRemainingArguments(args: Array<String>, startIndex: Int): Pair<String?, Boolean> {
        var output: String? = null
        var debugMode = false
        
        var i = startIndex
        while (i < args.size) {
            when (args[i]) {
                "--output" -> {
                    if (i + 1 < args.size) {
                        output = args[++i]
                    } else {
                        throw IllegalArgumentException("--output requires a value")
                    }
                }
                "--debug" -> {
                    debugMode = true
                }
                "--help", "-h" -> {
                    printUsage()
                    exitProcess(0)
                }
                else -> {
                    logger.warn("Unknown argument: ${args[i]}")
                }
            }
            i++
        }
        
        return Pair(output, debugMode)
    }

    /**
     * Prints usage information.
     */
    private fun printUsage() {
        println("""
            Bike Geometry Detector
            
            Usage: java -jar bike-geometry-detector.jar --input <image_path> [--output <output_dir>] [--debug]
            
            Options:
              <path_or_url>     Path to input bicycle image or image URL (if first argument)
              --input <path>    Path to input bicycle image or image URL (required if not first argument)
              --output <path>   Output directory for results (default: ./results)
              --debug           Enable verbose output and save intermediate images
              --help, -h        Show this help message
            
            Example:
              java -jar bike-geometry-detector.jar --input ./samples/bike1.jpg --output ./results/ --debug
        """.trimIndent())
    }

    /**
     * Generates debug filename based on base image name.
     */
    private fun generateDebugFilename(baseImagePath: String, outputDir: String, suffix: String, extension: String = "jpg"): String {
        val baseFile = File(baseImagePath)
        val baseName = baseFile.nameWithoutExtension
        return File(outputDir, "${baseName}_${suffix}.${extension}").absolutePath
    }

    /**
     * Main image processing pipeline.
     */
    private fun processImage(config: AppConfig) {
        logger.info("Processing image: ${config.inputPath}")
        
        if (config.debugMode) {
            logger.info("Debug mode enabled - intermediate images and verbose output will be saved")
            // Ensure output directory exists
            File(config.outputPath).mkdirs()
        }
        
        // Initialize components
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        val frameDetector = FrameDetector()
        val geometryCalculator = GeometryCalculator()
        val visualizer = Visualizer()
        
        try {
            // Step 1: Load and preprocess image
            logger.info("Step 1: Loading and preprocessing image")
            val imageData = imageLoader.loadAndPreprocess(config.inputPath, config)
            
            // Step 2: Detect wheels
            logger.info("Step 2: Detecting wheels")
            val detectedWheels = wheelDetector.detectWheels(imageData, config)
            
            if (detectedWheels.isEmpty()) {
                logger.warn("No wheels detected in the image")
            } else {
                logger.info("Detected ${detectedWheels.size} wheels")
                if (config.debugMode) {
                    outputWheelDetectionDetails(detectedWheels, config)
                }
            }
            
            // Step 3: Detect frame tubes
            logger.info("Step 3: Detecting frame tubes")
            val detectedFrameLines = frameDetector.detectFrameLines(imageData, detectedWheels, config)
            
            if (detectedFrameLines.isEmpty()) {
                logger.warn("No frame tubes detected in the image")
            } else {
                logger.info("Detected ${detectedFrameLines.size} frame lines")
                if (config.debugMode) {
                    outputFrameDetectionDetails(detectedFrameLines, config)
                }
            }
            
            // Step 4: Calculate geometry measurements
            logger.info("Step 4: Calculating geometry measurements")
            val measurements = geometryCalculator.calculateMeasurements(
                detectedWheels, detectedFrameLines, imageData
            )
            
            // Step 5: Calculate confidence scores
            val confidenceScores = geometryCalculator.calculateConfidenceScores(
                detectedWheels, detectedFrameLines, imageData
            )
            
            // Step 6: Create detection results
            val detectionResults = DetectionResults(
                wheelsFound = detectedWheels.size,
                wheelPositions = detectedWheels,
                frameTubesFound = detectedFrameLines.size,
                frameLines = detectedFrameLines,
                measurements = measurements,
                confidenceScores = confidenceScores
            )
            
            // Step 7: Generate visualization
            logger.info("Step 5: Generating visualization")
            val outputImagePath = visualizer.generateOutputFilename(config.inputPath, config.outputPath)
            visualizer.createAnnotatedImage(imageData, detectionResults, outputImagePath)
            
            // Step 8: Output results
            logger.info("Step 6: Outputting results")
            outputResults(detectionResults, config)
            
            logger.info("Processing completed successfully")
            
        } catch (e: Exception) {
            logger.error("Error during image processing: ${e.message}", e)
            throw e
        }
    }

    /**
     * Outputs detection results in JSON format.
     */
    private fun outputResults(results: DetectionResults, config: AppConfig) {
        // Create JSON output
        val jsonOutput = mapOf(
            "detection_results" to mapOf(
                "wheels_found" to results.wheelsFound,
                "wheel_positions" to results.wheelPositions.map { wheel ->
                    mapOf(
                        "x" to wheel.x.toInt(),
                        "y" to wheel.y.toInt(),
                        "radius" to wheel.radius.toInt()
                    )
                },
                "frame_tubes_found" to results.frameTubesFound,
                "measurements" to mapOf(
                    "wheelbase_pixels" to results.measurements.wheelbasePixels.toInt(),
                    "average_wheel_diameter_pixels" to results.measurements.averageWheelDiameterPixels.toInt()
                ),
                "confidence_scores" to mapOf(
                    "wheel_detection" to "%.2f".format(results.confidenceScores.wheelDetection),
                    "frame_detection" to "%.2f".format(results.confidenceScores.frameDetection)
                )
            )
        )
        
        // Print to console
        val jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput)
        println(jsonString)
        
        // Save to file
        val jsonFile = File(config.outputPath, "detection_results.json")
        jsonFile.parentFile?.mkdirs()
        jsonFile.writeText(jsonString)
        
        logger.info("Results saved to: ${jsonFile.absolutePath}")
        
        // Print summary
        printSummary(results)
    }

    /**
     * Prints a summary of detection results.
     */
    private fun printSummary(results: DetectionResults) {
        println("\n" + "=".repeat(50))
        println("BIKE GEOMETRY DETECTION SUMMARY")
        println("=".repeat(50))
        println("Wheels detected: ${results.wheelsFound}")
        println("Frame tubes detected: ${results.frameTubesFound}")
        println("Wheelbase: ${results.measurements.wheelbasePixels.toInt()} pixels")
        println("Average wheel diameter: ${results.measurements.averageWheelDiameterPixels.toInt()} pixels")
        println("Wheel detection confidence: ${"%.1f".format(results.confidenceScores.wheelDetection * 100)}%")
        println("Frame detection confidence: ${"%.1f".format(results.confidenceScores.frameDetection * 100)}%")
        
        if (results.wheelsFound < 2) {
            println("\nWARNING: Expected 2 wheels, but only ${results.wheelsFound} detected")
        }
        
        if (results.frameTubesFound < 3) {
            println("WARNING: Expected at least 3 frame tubes, but only ${results.frameTubesFound} detected")
        }
        
        println("=".repeat(50))
    }

    /**
     * Outputs detailed wheel detection information in debug mode.
     */
    private fun outputWheelDetectionDetails(wheels: List<DetectedCircle>, config: AppConfig) {
        val detailsText = buildString {
            appendLine("=== WHEEL DETECTION DETAILS ===")
            appendLine("Total wheels detected: ${wheels.size}")
            appendLine()
            wheels.forEachIndexed { index, wheel ->
                appendLine("Wheel ${index + 1}:")
                appendLine("  Position: (${wheel.x.toInt()}, ${wheel.y.toInt()})")
                appendLine("  Radius: ${wheel.radius.toInt()} pixels")
                appendLine("  Diameter: ${(wheel.radius * 2).toInt()} pixels")
                appendLine("  Confidence: ${"%.3f".format(wheel.confidence)}")
                appendLine()
            }
            if (wheels.size >= 2) {
                val leftWheel = wheels.minByOrNull { it.x }
                val rightWheel = wheels.maxByOrNull { it.x }
                if (leftWheel != null && rightWheel != null) {
                    val wheelbasePixels = abs(rightWheel.x - leftWheel.x)
                    appendLine("Calculated wheelbase: ${wheelbasePixels.toInt()} pixels")
                    appendLine("Average wheel diameter: ${wheels.map { it.radius * 2 }.average().toInt()} pixels")
                }
            }
            appendLine("=== END WHEEL DETECTION DETAILS ===")
        }
        
        logger.info("Debug: Wheel detection details:\n$detailsText")
        
        // Save detailed text output
        val debugTextPath = generateDebugFilename(config.inputPath, config.outputPath, "wheel_details", "txt")
        File(debugTextPath).writeText(detailsText)
        logger.info("Debug: Saved wheel detection details to: $debugTextPath")
    }
    
    /**
     * Outputs detailed frame detection information in debug mode.
     */
    private fun outputFrameDetectionDetails(frameLines: List<DetectedLine>, config: AppConfig) {
        val detailsText = buildString {
            appendLine("=== FRAME DETECTION DETAILS ===")
            appendLine("Total frame lines detected: ${frameLines.size}")
            appendLine()
            frameLines.forEachIndexed { index, line ->
                appendLine("Frame Line ${index + 1}:")
                appendLine("  Start point: (${line.x1.toInt()}, ${line.y1.toInt()})")
                appendLine("  End point: (${line.x2.toInt()}, ${line.y2.toInt()})")
                appendLine("  Length: ${"%.1f".format(line.length)} pixels")
                appendLine("  Angle: ${"%.1f".format(line.angle)}°")
                appendLine("  Confidence: ${"%.3f".format(line.confidence)}")
                appendLine()
            }
            
            // Group lines by approximate angle for analysis
            val angleGroups = frameLines.groupBy { (it.angle / 15).toInt() * 15 }
            appendLine("Lines grouped by angle (±15°):")
            angleGroups.forEach { (angle, lines) ->
                appendLine("  ~${angle}°: ${lines.size} lines")
            }
            
            appendLine("=== END FRAME DETECTION DETAILS ===")
        }
        
        logger.info("Debug: Frame detection details:\n$detailsText")
        
        // Save detailed text output
        val debugTextPath = generateDebugFilename(config.inputPath, config.outputPath, "frame_details", "txt")
        File(debugTextPath).writeText(detailsText)
        logger.info("Debug: Saved frame detection details to: $debugTextPath")
    }

    /**
     * Configuration for the application.
     */
    data class AppConfig(
        val inputPath: String,
        val outputPath: String,
        val debugMode: Boolean = false
    )
}