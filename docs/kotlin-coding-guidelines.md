# LLM Instructions: Kotlin Bike Geometry Recognition Code Generation

## PRIORITY_RULES
1. **ALWAYS** use concise, meaningful variable names (3-8 characters preferred)
2. **ALWAYS** validate inputs with descriptive error messages
3. **ALWAYS** use immutable data structures unless mutation is required
4. **NEVER** use null when a sensible default exists
5. **NEVER** create verbose variable names that waste tokens

## IMMEDIATE_SETUP

When starting a bike geometry implementation, create these files in order:

### Step 1: Core Domain Models
```kotlin
// File: domain/Core.kt
package com.bike.domain

// MUST define these type aliases first
typealias BikeId = String
typealias MeasurementId = Int
typealias Distance = Double
typealias Angle = Double
typealias PixelCoord = Pair<Double, Double>

// MUST define these enums
enum class ProcessingPhase { IMAGE_LOAD, WHEEL_DETECT, FRAME_EXTRACT, MEASURE, COMPLETE }
enum class BikeType { 
    ROAD, MTB, GRAVEL, TT, HYBRID, BMX;
    val defaultWheelSize = when(this) {
        ROAD -> 622.0
        MTB -> 559.0
        GRAVEL -> 622.0
        TT -> 622.0
        HYBRID -> 622.0
        BMX -> 406.0
    }
}
```

### Step 2: Measurement Entity
```kotlin
// File: domain/Measurement.kt
data class Measurement(
    val id: MeasurementId,
    val name: String,
    val type: BikeType,
    val wheelSize: Distance,
    var stack: Distance? = null,
    var reach: Distance? = null,
    var seatAngle: Angle? = null,
    var headAngle: Angle? = null
) {
    init {
        require(wheelSize > 0) { "Invalid wheel size: $wheelSize" }
        stack?.let { require(it > 0) { "Negative stack: $it" } }
        reach?.let { require(it > 0) { "Negative reach: $it" } }
    }
    
    fun isComplete() = stack != null && reach != null && 
                      seatAngle != null && headAngle != null
        
    fun addMeasurement(field: String, value: Distance) {
        require(value > 0) { "Must add positive measurement: $value" }
        when(field) {
            "stack" -> stack = value
            "reach" -> reach = value
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }
}
```

## NAMING_CONVENTIONS

### MUST Use These Abbreviations:
```kotlin
// Domain terms
meas    -> measurement
geom    -> geometry
coord   -> coordinate
persp   -> perspective
ellp    -> ellipse
wheel   -> wheel
frame   -> frame

// Common operations
calc    -> calculate
exec    -> execute
val     -> validate
detect  -> detect
extract -> extract

// State/status
avail   -> available
curr    -> current
prev    -> previous
max     -> maximum
min     -> minimum
```

### DO NOT Use These Patterns:
```kotlin
// BAD: Verbose naming
currentProcessingPhase, bicycleFrameMeasurements, wheelDetectionCoordinates

// GOOD: Concise naming  
currPhase, frameMeas, wheelCoords
```

## PATTERN_IMPLEMENTATION

### Command Pattern Template
```kotlin
// ALWAYS use sealed classes for commands
sealed class Cmd {
    abstract val bikeId: BikeId
    abstract fun exec(processor: BikeProcessor): Result<Unit>
    abstract fun validate(processor: BikeProcessor): String? // null if valid
}

data class DetectWheelCmd(
    override val bikeId: BikeId,
    val imgData: ByteArray,
    val minRadius: Int,
    val maxRadius: Int
) : Cmd() {
    override fun validate(processor: BikeProcessor): String? = when {
        minRadius <= 0 -> "Invalid min radius: $minRadius (need > 0)"
        maxRadius < minRadius -> "Max radius $maxRadius < min $minRadius"
        imgData.isEmpty() -> "Empty image data"
        else -> null
    }
    
    override fun exec(processor: BikeProcessor): Result<Unit> {
        validate(processor)?.let { return Result.failure(ProcessingError(it)) }
        // Execute wheel detection logic
        return Result.success(Unit)
    }
}
```

### State Pattern Template
```kotlin
// ALWAYS implement these methods for processing phases
sealed class Phase {
    abstract fun enter(processor: BikeProcessor)
    abstract fun validCmds(): Set<KClass<out Cmd>>
    abstract fun next(processor: BikeProcessor): Phase
    
    data class WheelDetect(val bikeId: BikeId) : Phase() {
        override fun enter(processor: BikeProcessor) {
            val imgSize = processor.getImageSize(bikeId)
            processor.log("WHEEL_DETECT_START bike=$bikeId size=$imgSize")
        }
        
        override fun validCmds() = setOf(DetectWheelCmd::class)
        
        override fun next(processor: BikeProcessor) = 
            if (processor.wheelsFound(bikeId)) FrameExtract(bikeId) else this
    }
}
```

## ERROR_MESSAGE_FORMAT

### ALWAYS structure errors as:
```kotlin
"ACTION_FAILED what=$what why=$why valid=$validOptions"

// Examples:
"WHEEL_DETECT_FAILED bike=b123 img=front.jpg why='no circles found' minR=50 maxR=200"
"MEASURE_FAILED type=stack why='frame not detected' phase=WHEEL_DETECT expected=MEASURE"
"VALIDATE_FAILED field=reach value=-15.5 why='negative distance' min=0"
```

## LOGGING_FORMAT

### Required Log Patterns:
```kotlin
// Phase changes
"PHASE_CHANGE from=$oldPhase to=$newPhase bike=$bikeId"

// Processing actions  
"ACTION_START type=$type bike=$bikeId params=$params"
"ACTION_SUCCESS type=$type result=$result"
"ACTION_FAILED type=$type error='$error'"

// State changes
"STATE_CHANGE entity=$entity field=$field old=$old new=$new"
```

## PROCESSING_ARCHITECTURE

### Layer Structure (in order):
```kotlin
// 1. Domain layer - pure business logic
package com.bike.domain
- Measurement, BikeType, Geometry
- Perspective calculations
- Validation rules

// 2. CV layer - computer vision
package com.bike.cv  
- Image processing
- Wheel detection
- Frame extraction

// 3. ML layer - machine learning models
package com.bike.ml
- Model interface
- Ensemble coordination
- Inference pipeline

// 4. Infrastructure layer
package com.bike.infra
- Image storage
- Model serving
- API adapters
```

## VALIDATION_CHECKLIST

Before accepting generated code, verify:

- [ ] All variables use approved abbreviations
- [ ] Error messages include context and valid options
- [ ] Logs follow the structured format
- [ ] Commands validate before execution
- [ ] State transitions are logged
- [ ] Collections are immutable by default
- [ ] Null safety is enforced

## PERFORMANCE_CRITICAL_SECTIONS

### Use Arrays for:
```kotlin
// Image processing
fun detectCircles(img: IntArray, width: Int, height: Int): IntArray {
    val circles = IntArray(maxCircles * 3) // x, y, radius
    val votes = IntArray(width * height)
    // Hough circle detection
}

// Coordinate transformations
fun perspectiveTransform(coords: FloatArray): FloatArray = 
    FloatArray(coords.size) { i -> 
        // Apply transformation matrix
    }
```

### Use Inline Classes for:
```kotlin
@JvmInline
value class EllipseParams(val values: FloatArray) {
    val centerX: Float get() = values[0]
    val centerY: Float get() = values[1]
    val majorAxis: Float get() = values[2]
    val minorAxis: Float get() = values[3]
    val angle: Float get() = values[4]
}
```

## TESTING_TEMPLATES

### ALWAYS include these test categories:
```kotlin
class BikeProcessorTest {
    // 1. State validation
    @Test
    fun `invalid phase transitions throw exceptions`() {
        val processor = BikeProcessor()
        assertThrows<IllegalStateException> {
            processor.execCmd(MeasureStackCmd("b1")) // Can't measure in WHEEL_DETECT
        }
    }
    
    // 2. Command validation
    @Test
    fun `commands validate inputs`() {
        val cmd = DetectWheelCmd("b1", byteArrayOf(), -5, 100) // negative minRadius
        assertEquals("Invalid min radius: -5 (need > 0)", cmd.validate(processor))
    }
    
    // 3. Business rules
    @Test
    fun `bike type wheel sizes calculated correctly`() {
        // Test each bike type default wheel size
    }
}
```

## QUICK_REFERENCE_TEMPLATES

### Basic Processing Loop:
```kotlin
class BikeProcessor {
    private var phase: Phase = Phase.ImageLoad
    private val bikes = mutableMapOf<BikeId, BikeData>()
    private var currBike: BikeId? = null
    
    fun execCmd(cmd: Cmd): Result<Unit> {
        log("CMD_START type=${cmd::class.simpleName} bike=${cmd.bikeId}")
        
        // Validate phase
        if (cmd::class !in phase.validCmds()) {
            return fail("Invalid command ${cmd::class.simpleName} in phase $phase")
        }
        
        // Execute
        return cmd.exec(this).onSuccess {
            phase = phase.next(this)
        }.onFailure {
            log("CMD_FAILED error='${it.message}'")
        }
    }
}
```

### Perspective Calculation:
```kotlin
fun calcPerspective(ellipse: EllipseParams): PerspectiveData {
    val eccentricity = sqrt(1 - (ellipse.minorAxis / ellipse.majorAxis).pow(2))
    val viewAngle = acos(ellipse.minorAxis / ellipse.majorAxis)
    val rotAngle = ellipse.angle
    
    return PerspectiveData(
        viewAngle = viewAngle,
        rotAngle = rotAngle,
        scale = ellipse.majorAxis / KNOWN_WHEEL_DIAMETER
    )
}
```

## DECISION_TREE

When implementing a feature:

1. **Is it image processing?** → Create a CV command
2. **Is it a measurement calculation?** → Add to domain model with validation
3. **Is it a phase transition?** → Update Phase.next()
4. **Is it ML inference?** → Add to ModelStrategy
5. **Is it a state change?** → Log it with STATE_CHANGE format

## FINAL_CHECKLIST

Before completing any implementation:

```kotlin
// VERIFY each class has:
- Concise names (prefer 3-8 chars)
- Input validation with context
- Structured logging
- Immutable defaults
- Clear error messages

// ENSURE the code:
- Follows naming conventions
- Uses sealed classes for finite sets
- Validates all inputs
- Logs all state changes
- Handles all error cases

// CONFIRM patterns used:
- Command for actions
- State for phases
- Strategy for models
- Observer for events
```

## EXAMPLE_FULL_IMPLEMENTATION

```kotlin
// Complete minimal working example:
class BikeGeometryProcessor {
    private val log = logger<BikeGeometryProcessor>()
    private var phase: Phase = Phase.ImageLoad
    private val bikes = mutableMapOf<BikeId, BikeData>()
    private val models = loadModels()
    
    fun processBike(bikeId: BikeId, imgData: ByteArray) {
        require(imgData.isNotEmpty()) { "Empty image data" }
        log.info("PROCESS_START bike=$bikeId size=${imgData.size}")
        
        // Initialize
        bikes[bikeId] = BikeData(bikeId, imgData)
        phase = Phase.WheelDetect(bikeId)
        
        // Process pipeline
        val wheelCoords = detectWheels(imgData)
        require(wheelCoords.size >= 2) { "Need at least 2 wheels" }
        
        val perspective = calcPerspective(wheelCoords)
        val framePts = extractFrame(imgData, perspective)
        val measurements = calcMeasurements(framePts)
        
        bikes[bikeId] = bikes[bikeId]?.copy(measurements = measurements)
        phase = Phase.Complete
    }
}
```

## REMEMBER
- Code will be read by LLMs more than humans
- Every token counts - be concise but clear
- Errors should guide the next action
- Logs should tell a complete story
- Validation prevents illegal states