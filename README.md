# Bike Geometry Detector

A Kotlin prototype application for detecting bicycle wheel and frame geometry using OpenCV computer vision algorithms.

## Overview

This application processes bicycle images to detect wheels using HoughCircles and frame tubes using line segment detection. It outputs annotated images with detected components overlaid and provides structured JSON results with measurements.

## Features

- **Wheel Detection**: Uses HoughCircles algorithm to detect bicycle wheels
- **Frame Detection**: Uses Line Segment Detector (LSD) to identify frame tubes
- **Geometry Calculations**: Computes wheelbase, wheel diameters, and perspective correction
- **Visualization**: Generates annotated images with detection overlays
- **JSON Output**: Structured results with confidence scores and measurements
- **Error Handling**: Graceful handling of detection failures and missing components

## Requirements

- **Java**: JDK 11 or higher
- **Gradle**: 7.0 or higher (included via wrapper)
- **Input Images**: 
  - Format: JPEG or PNG
  - Recommended resolution: 1024x768 to 4096x3072 pixels
  - Clear side view of bicycle with good lighting
  - Minimal background clutter for best results

## Installation

### 1. Clone the Repository
```bash
git clone https://github.com/csp75/bike-size.git
cd bike-size
```

### 2. Build the Application
```bash
./gradlew build
```

### 3. Run Tests
```bash
./gradlew test
```

## Usage

### Command Line Interface

```bash
./gradlew run --args="--input <image_path> [--output <output_dir>]"
```

#### Options

- `--input <path>`: Path to input bicycle image (required)
- `--output <path>`: Output directory for results (default: ./results)
- `--help`, `-h`: Show help message

#### Examples

```bash
# Basic usage
./gradlew run --args="--input ./samples/bike1.jpg"

# Specify output directory
./gradlew run --args="--input ./samples/bike1.jpg --output ./my_results/"

# Show help
./gradlew run --args="--help"
```

### Using the JAR File

```bash
# Build distribution
./gradlew distZip

# Extract and run
unzip build/distributions/bike-geometry-detector-1.0.0.zip
./bike-geometry-detector-1.0.0/bin/bike-geometry-detector --input ./samples/bike1.jpg
```

## Output

### 1. Annotated Image
- Filename: `{input_name}_detected.jpg`
- Green circles: Detected wheels with radius indicators
- Blue lines: Detected frame tubes with line numbers
- White text overlay: Measurements and detection counts
- Confidence bars: Color-coded confidence indicators

### 2. JSON Results
Example output saved to `detection_results.json`:

```json
{
  "detection_results": {
    "wheels_found": 2,
    "wheel_positions": [
      {"x": 250, "y": 400, "radius": 150},
      {"x": 750, "y": 400, "radius": 150}
    ],
    "frame_tubes_found": 5,
    "measurements": {
      "wheelbase_pixels": 500,
      "average_wheel_diameter_pixels": 300
    },
    "confidence_scores": {
      "wheel_detection": "0.95",
      "frame_detection": "0.82"
    }
  }
}
```

### 3. Console Summary
```
==================================================
BIKE GEOMETRY DETECTION SUMMARY
==================================================
Wheels detected: 2
Frame tubes detected: 5
Wheelbase: 500 pixels
Average wheel diameter: 300 pixels
Wheel detection confidence: 95.0%
Frame detection confidence: 82.0%
==================================================
```

## Algorithm Details

### Wheel Detection (HoughCircles)
- **Algorithm**: OpenCV's HoughCircles with HOUGH_GRADIENT method
- **Parameters**:
  - dp = 1.2 (inverse ratio of accumulator resolution)
  - minDist = image.height / 8 (minimum distance between centers)
  - param1 = 100 (Canny edge detector threshold)
  - param2 = 30 (accumulator threshold for circle centers)
  - minRadius = image.height / 20
  - maxRadius = image.height / 3

### Frame Detection (Line Segments)
- **Algorithm**: OpenCV's HoughLinesP (Probabilistic Hough Transform)
- **Preprocessing**: Canny edge detection with thresholds 50-150
- **Parameters**:
  - rho = 1.0 (distance resolution)
  - theta = π/180 (angle resolution)
  - threshold = 50 (minimum line length)
  - minLineLength = 20 pixels
  - maxLineGap = 10 pixels

### Post-Processing
- **Wheel Validation**: Checks for reasonable wheelbase distance and similar wheel sizes
- **Frame Filtering**: Groups parallel lines and filters by confidence and position
- **Confidence Scoring**: Based on geometric consistency and detection quality

## Project Structure

```
src/main/kotlin/com/bikesize/
├── BikeGeometryDetector.kt    # Main application and CLI handling
├── DataModels.kt              # Data classes for detected components
├── ImageLoader.kt             # Image loading and preprocessing
├── WheelDetector.kt           # Wheel detection using HoughCircles
├── FrameDetector.kt           # Frame tube detection using line segments
├── GeometryCalculator.kt      # Geometric measurements and calculations
└── Visualizer.kt              # Annotated image generation

src/test/kotlin/com/bikesize/
└── BikeGeometryDetectorTest.kt # Unit tests

samples/                       # Sample input images (add your own)
results/                       # Output directory for processed results
```

## Configuration

Detection parameters can be customized by modifying the `DetectionConfig` data class in `DataModels.kt`:

```kotlin
data class DetectionConfig(
    val houghCirclesDp: Double = 1.2,
    val houghCirclesMinDist: Double = 0.125,
    val houghCirclesParam1: Double = 100.0,
    val houghCirclesParam2: Double = 30.0,
    val houghCirclesMinRadius: Double = 0.05,
    val houghCirclesMaxRadius: Double = 0.33,
    val minLineLength: Int = 20,
    val maxLineGap: Int = 10,
    val lineAngleTolerance: Double = 5.0
)
```

## Troubleshooting

### Common Issues

1. **No wheels detected**
   - Ensure bicycle is clearly visible in side view
   - Check image resolution (minimum 1024x768 recommended)
   - Reduce background clutter
   - Adjust HoughCircles parameters for your image type

2. **Poor frame detection**
   - Ensure good contrast between frame and background
   - Check for sufficient lighting
   - Try preprocessing the image (contrast enhancement)

3. **OpenCV initialization errors**
   - Ensure Java 11+ is installed
   - Check that OpenCV native libraries are properly loaded
   - Try running with `-Djava.library.path` if needed

### Performance Tips

- **Image Size**: Resize large images (>4MP) for faster processing
- **Preprocessing**: Convert to optimal contrast before processing
- **Parameters**: Tune detection parameters for your specific image types

## Limitations

- **Current Version**: Focuses on classical CV methods only (no ML models)
- **Perspective**: Assumes side-view bicycle images
- **Background**: Works best with minimal background clutter
- **Lighting**: Requires good, even lighting for optimal results

## Future Enhancements

- Integration of machine learning models for improved detection
- 3D perspective correction
- Multi-angle image support
- Real-time video processing
- Advanced frame geometry analysis (seat tube angle, head tube angle, etc.)

## Dependencies

- **OpenCV**: 4.9.0-0 (Computer vision library)
- **Kotlin**: 1.9.20 (JVM target)
- **SLF4J + Logback**: Logging framework
- **Jackson**: JSON processing
- **JUnit 5**: Testing framework

## License

This project is part of the bike-size repository. See the repository license for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review existing issues in the repository
3. Create a new issue with detailed description and sample images