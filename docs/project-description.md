# Kotlin Bike Geometry Measurement System - Technical Implementation Plan

## Executive Summary
Modular computer vision system for automatic bike geometry extraction from images using ensemble of micro-models. Wheel ellipse distortion analysis determines 3D orientation, enabling accurate frame measurements. Dual deployment: cloud service (full accuracy) and offline Android app (mobile-optimized).

## 1. Core Technical Architecture

### 1.1 Modular Micro-Model Ensemble
- **Geometric Primitive Models** (15MB total)
  - Circle/Ellipse Detector: Wheel detection, bearing detection
  - Line Segment Detector: Frame tube identification  
  - Corner/Junction Detector: Frame connection points
  - Angle Estimator: Tube angle calculations
  
- **Measurement Models** (10MB total)
  - Edge Refinement Network: Sub-pixel boundary accuracy
  - Perspective Correction Model: Viewing angle from ellipse eccentricity
  - Distance Calibrator: Pixel-to-mm conversion using wheel reference
  
- **Semantic Models** (8MB total)
  - Bike Part Classifier: Component identification (wheel/frame/cockpit)
  - Material Classifier: Surface type for optimal edge detection
  - Occlusion Detector: Identify obscured components

### 1.2 Model Reusability Matrix
- Each model serves 3-5 different functions
- Shared feature extractors reduce memory footprint
- Dynamic pipeline construction based on image quality

### 1.3 Processing Pipeline Architecture
```
Input Image → Part Detection → Parallel Processing:
                                ├─ Wheel Pipeline (2x)
                                ├─ Frame Pipeline
                                └─ Validation Pipeline
                                        ↓
                              Geometry Aggregation → Output
```

## 2. Computer Vision Processing

### 2.1 Wheel-Based Perspective Analysis
- **Primary Innovation**: Rear wheel ellipse parameters define frame plane
- **Dual-Wheel Triangulation**: Front/rear wheel positions determine steering angle
- **Mathematical Foundation**: 
  - Ellipse eccentricity → viewing angle
  - Major axis orientation → rotation angle
  - Known wheel diameter (622mm/559mm) → absolute scale

### 2.2 Geometry Extraction Pipeline
- **Stage 1**: Detect both wheels using circular object detector
- **Stage 2**: Fit precise ellipses to wheel boundaries
- **Stage 3**: Calculate 3D bike orientation from ellipse parameters
- **Stage 4**: Apply perspective correction to frame region
- **Stage 5**: Extract frame lines and junction points
- **Stage 6**: Calculate standard measurements (stack, reach, angles)

### 2.3 Accuracy Optimization
- Multi-scale processing for sub-pixel precision
- Geometric constraint validation between measurements
- Cross-validation using multiple measurement paths

## 3. Implementation Architecture

### 3.1 Technology Stack
- **Language**: Kotlin (JVM backend, Android app)
- **CV Framework**: OpenCV 4.10+ with Kotlin bindings
- **ML Runtime**: ONNX Runtime (cross-platform deployment)
- **DI Framework**: Hilt for dependency management
- **Architecture**: Clean Architecture with hexagonal patterns

### 3.2 Module Structure
```
bike-geometry-system/
├── core/
│   ├── domain/           # Business logic, measurements
│   ├── cv-models/        # Micro-model definitions
│   └── algorithms/       # Geometric calculations
├── training/
│   ├── data-pipeline/    # Scraping, augmentation
│   ├── model-training/   # Individual model training
│   └── ensemble-tuning/  # End-to-end optimization
├── service/
│   ├── api/             # REST endpoints
│   ├── processing/      # Async job processing
│   └── inference/       # Model serving
└── android/
    ├── camera/          # Image capture
    ├── ml-runtime/      # TFLite/ONNX mobile
    └── offline-cache/   # Model storage
```

### 3.3 Data Flow Architecture
- **Input Sources**: Camera API, uploaded images, batch processing
- **Processing Queue**: Redis-backed job queue for async processing
- **Result Storage**: PostgreSQL for production, MongoDB for development
- **Cache Layer**: Multi-level caching (Redis → in-memory → disk)

## 4. Training Infrastructure

### 4.1 Dataset Construction
- **Primary Sources**: 
  - Bike manufacturer websites (Trek, Specialized, Canyon)
  - Online marketplaces (eBay, Craigslist listings)
  - Geometry databases (geometry.bike, 99spokes)
- **Augmentation Strategy**:
  - Synthetic perspective variations
  - Lighting/shadow simulation
  - Partial occlusion generation

### 4.2 Model Training Pipeline
- **Individual Model Training**: Each micro-model trained on specific task
- **Ensemble Optimization**: End-to-end fine-tuning with weighted losses
- **Validation Strategy**: Cross-reference with manufacturer specifications

### 4.3 Continuous Learning
- User feedback integration
- Active learning for edge cases
- A/B testing different model versions

## 5. Deployment Strategy

### 5.1 Cloud Service Architecture
- **Container**: Docker with NVIDIA GPU support
- **Orchestration**: Kubernetes with HPA based on GPU utilization
- **Scaling**: 2-10 pods, targeting <2s processing time
- **API Design**: Async REST with webhook callbacks

### 5.2 Android App Architecture
- **Model Format**: TensorFlow Lite quantized models
- **Total Size**: 50-80MB APK with all models
- **Performance**: 1-3 seconds on modern phones
- **Offline Mode**: Full functionality without internet

### 5.3 Model Serving Optimization
- Model pooling for concurrent requests
- GPU memory management
- Dynamic batching for throughput
- Progressive resolution processing

## 6. Quality Assurance

### 6.1 Accuracy Metrics
- **Target**: ±2mm for major measurements
- **Validation**: Against manufacturer specifications
- **Test Dataset**: 1000+ manually measured bikes

### 6.2 Performance Requirements
- **Latency**: <2s for single image (cloud), <5s (mobile)
- **Throughput**: 100 images/minute per GPU
- **Availability**: 99.9% uptime SLA

### 6.3 Error Handling
- Graceful degradation for poor quality images
- Confidence scores for all measurements
- Alternative measurement paths

## 7. Development Phases

### Phase 1: Core Models (Month 1-2)
- Implement and train geometric primitive detectors
- Build ellipse-based perspective calculation
- Create basic measurement pipeline

### Phase 2: Ensemble System (Month 3-4)
- Integrate micro-models into pipelines
- Implement model registry and dynamic loading
- Build validation and constraint systems

### Phase 3: Service Development (Month 5-6)
- REST API with async processing
- Database and caching layers
- Monitoring and logging infrastructure

### Phase 4: Mobile App (Month 7-8)
- Android app with CameraX integration
- Model optimization for mobile
- Offline processing capability

### Phase 5: Production Hardening (Month 9-10)
- Scale testing and optimization
- Security and authentication
- Documentation and deployment automation

## 8. Technical Risks and Mitigations

### 8.1 Primary Risks
- **Model Accuracy**: Mitigate with ensemble validation
- **Processing Speed**: GPU optimization and caching
- **Mobile Performance**: Progressive processing strategies
- **Dataset Quality**: Active learning and user feedback

### 8.2 Fallback Strategies
- Manual measurement tools for edge cases
- Hybrid human-AI validation for critical measurements
- Progressive enhancement based on device capabilities

## 9. Success Metrics
- 95% measurement accuracy within ±3mm
- <2 second average processing time
- 90% single-image success rate
- 50MB mobile app size
- 10,000 images/day capacity per server

## 10. Next Steps
1. Prototype wheel detection and ellipse fitting
2. Validate perspective calculation mathematics
3. Collect initial training dataset (100 bikes)
4. Build proof-of-concept ensemble pipeline
5. Test on diverse bike types and image qualities