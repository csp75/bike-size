package com.bikesize

import java.io.File

/**
 * Utility class for file operations including filename generation and versioning.
 */
object FileUtils {
    
    /**
     * Generates a debug filename with a suffix based on the input image path.
     * 
     * @param baseImagePath The path to the base image file
     * @param outputDir The output directory where the file will be saved
     * @param suffix The suffix to append to the filename (e.g., "wheel_detection", "frame_lines")
     * @param extension The file extension (default: "jpg")
     * @param overwrite Whether to overwrite existing files (default: false)
     * @return The full path to the generated filename
     */
    fun generateDebugFilename(baseImagePath: String, outputDir: String, suffix: String, extension: String = "jpg", overwrite: Boolean = false): String {
        val baseFile = File(baseImagePath)
        val baseName = baseFile.nameWithoutExtension
        return generateVersionedFilename(outputDir, "${baseName}_${suffix}", extension, overwrite)
    }

    /**
     * Generates a versioned filename that either overwrites or adds incrementing suffix.
     * 
     * @param outputDir The output directory where the file will be saved
     * @param baseName The base name for the file (without extension)
     * @param extension The file extension
     * @param overwrite Whether to overwrite existing files
     * @return The full path to the generated filename
     */
    fun generateVersionedFilename(outputDir: String, baseName: String, extension: String, overwrite: Boolean): String {
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