package com.bikesize

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import nu.pattern.OpenCV
import org.slf4j.LoggerFactory
import java.io.File
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
        
        // Check if first argument is a direct path/URL (doesn't start with --)
        if (args.isNotEmpty() && !args[0].startsWith("--")) {
            inputPath = args[0]
            // Parse remaining arguments starting from index 1
            outputPath = parseRemainingArguments(args, 1)
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
            outputPath = outputDir
        )
    }

    /**
     * Parses remaining arguments after the first path/URL argument.
     */
    private fun parseRemainingArguments(args: Array<String>, startIndex: Int): String? {
        var output: String? = null
        
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
        
        return output
    }

    /**
     * Prints usage information.
     */
    private fun printUsage() {
        println("""
            Bike Geometry Detector
            
            Usage: 
              java -jar bike-geometry-detector.jar <image_path_or_url> [--output <output_dir>]
              java -jar bike-geometry-detector.jar --input <image_path_or_url> [--output <output_dir>]
            
            Options:
              <path_or_url>     Path to input bicycle image or image URL (if first argument)
              --input <path>    Path to input bicycle image or image URL (required if not first argument)
              --output <path>   Output directory for results (default: ./results)
              --help, -h        Show this help message
            
            Examples:
              java -jar bike-geometry-detector.jar ./samples/bike1.jpg
              java -jar bike-geometry-detector.jar https://example.com/bike.jpg --output ./results/
              java -jar bike-geometry-detector.jar --input ./samples/bike1.jpg --output ./results/
        """.trimIndent())
    }

    /**
     * Main image processing pipeline.
     */
    private fun processImage(config: AppConfig) {
        logger.info("Processing image: ${config.inputPath}")
        
        // Initialize components
        val imageLoader = ImageLoader()
        val wheelDetector = WheelDetector()
        val frameDetector = FrameDetector()
        val geometryCalculator = GeometryCalculator()
        val visualizer = Visualizer()
        
        try {
            // Step 1: Load and preprocess image
            logger.info("Step 1: Loading and preprocessing image")
            val imageData = imageLoader.loadAndPreprocess(config.inputPath)
            
            // Step 2: Detect wheels
            logger.info("Step 2: Detecting wheels")
            val detectedWheels = wheelDetector.detectWheels(imageData)
            
            if (detectedWheels.isEmpty()) {
                logger.warn("No wheels detected in the image")
            } else {
                logger.info("Detected ${detectedWheels.size} wheels")
            }
            
            // Step 3: Detect frame tubes
            logger.info("Step 3: Detecting frame tubes")
            val detectedFrameLines = frameDetector.detectFrameLines(imageData, detectedWheels)
            
            if (detectedFrameLines.isEmpty()) {
                logger.warn("No frame tubes detected in the image")
            } else {
                logger.info("Detected ${detectedFrameLines.size} frame lines")
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
     * Configuration for the application.
     */
    data class AppConfig(
        val inputPath: String,
        val outputPath: String
    )
}