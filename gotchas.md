# TextProc Gotchas and Edge Cases

This document contains important edge cases, known issues, and potential gotchas when using the TextProc application.

## PDF Processing Issues

### Corrupted PDF Files

**Issue**: The application may encounter corrupted or incomplete PDF files that cause extraction to fail.

**Symptoms**:
- Error: `Missing root object specification in trailer`
- Stack trace containing `TIKA-198: Illegal IOException from org.apache.tika.parser.pdf.PDFParser`
- Log message: `No chunks were generated for file`

**Root Cause**: 
- PDF file is corrupted, incomplete, or malformed
- File was not completely downloaded from the source
- Source file has structural issues in the PDF format

**Resolution**:
1. **Verify file integrity**: Check if the file can be opened in a PDF viewer
2. **Re-download**: If the file is from a remote source, try downloading it again
3. **Check source**: Verify the original file source is not corrupted
4. **Alternative tools**: Try processing the file with other PDF tools to confirm corruption

**Application Behavior**:
- The application gracefully handles corrupted PDFs
- Error is logged with specific categorization (CORRUPTED PDF detected)
- Processing continues with other files (no crash)
- Returns empty result for the corrupted file

**Prevention**:
- Implement file integrity checks before processing
- Add checksums or file validation where possible
- Monitor download processes for completeness

### Text Chunking Issues ⭐ **FIXED**

**Issue**: All files were showing as "1 chunk" regardless of file size, even for very large PDFs.

**Symptoms**:
- Large files (81MB, 263MB) reporting only 1 chunk
- Chunk size of 256KB but chunk count always 1
- Files much larger than chunk size not being split properly

**Root Cause**: 
- **Token vs Byte Confusion**: The `TokenTextSplitter` expects token counts, but was receiving byte counts
- Passing 131,072 tokens (256KB ÷ 2) instead of appropriate token sizes (1000-2000 tokens)

**Resolution**:
- Fixed chunking logic to convert byte-based chunk sizes to appropriate token counts
- Implemented streaming extraction for large files (>50MB) to prevent memory issues
- Added conservative token settings for large files (500-1000 tokens per chunk)

**Result**: Large files now properly split into multiple chunks based on content size.

## Memory Management Issues ⭐ **FIXED**

### Large File Memory Errors

**Issue**: Very large PDF files (70MB+) were causing direct buffer memory exhaustion.

**Symptoms**:
- Error: `Cannot reserve X bytes of direct buffer memory (allocated: Y, limit: 10485760)`
- Files failing to process due to memory allocation failures
- Application crashing when processing large files

**Root Cause**:
- Apache Tika trying to load entire large files into memory at once
- Default direct buffer memory limit of 10MB insufficient for large files
- No streaming approach for large file processing

**Resolution**:
1. **Increased direct buffer memory** via JVM settings (`-XX:MaxDirectMemorySize=200m`)
2. **Implemented streaming extraction** for files >50MB using `InputStreamResource`
3. **Conservative chunking** for large files to prevent memory issues
4. **Memory threshold detection** to automatically use streaming for large files

**Result**: Large files (79MB+) now process successfully without memory errors.

## File Processing Tracking Issues ⭐ **FIXED**

### Failed Files Marked as Processed

**Issue**: Files that failed processing were still being marked as "already processed", preventing retry.

**Symptoms**:
- Failed files showing "Skipping already processed file" in logs
- No retry mechanism for files that failed due to memory or extraction issues
- Files permanently skipped even after fixes were deployed

**Root Cause**:
- File tracking was happening **before** successful processing
- `processedFiles.put(fileKey, true)` was called immediately when processing started
- Failed files remained in the tracking map, preventing retry

**Resolution**:
- Moved file tracking to **after** successful completion
- Only mark files as processed after successful HDFS write
- Added logging to track successful processing completion

**Result**: Failed files can now be retried after fixes are deployed.

## HDFS Writing Issues ⭐ **FIXED**

### Filename Encoding Problems

**Issue**: Files with spaces or special characters in names failed to write to HDFS.

**Symptoms**:
- Error: `Illegal character SPACE=' '`
- HDFS write failures with HTTP 400 responses
- Processed files not appearing in `/processed_files/` directory

**Root Cause**:
- Filenames with spaces not being URL-encoded for HDFS write operations
- WebHDFS REST API rejecting unencoded special characters in paths

**Resolution**:
- Added URL encoding for filenames in `writeProcessedFileToHdfs` method
- Properly encode special characters and spaces before writing to HDFS
- Maintain original filename for local processing while using encoded version for HDFS

**Result**: Files with spaces and special characters now write successfully to HDFS.

## UI Enhancements ⭐ **ADDED**

### Clickable File Links

**Feature**: Processed files now have clickable links in the UI to view extracted text.

**Implementation**:
- Processed text files are stored in HDFS `/processed_files/` directory
- Controller endpoint reads from HDFS using WebHDFS REST API
- UI template updated with clickable filename links
- New tab opens with processed text content from HDFS

**Usage**: Click on any filename in the processed files list to view the extracted text content.

**Technical Details**:
- Files are served from: `http://35.196.56.130:9870/webhdfs/v1/processed_files/{filename}.txt`
- URL encoding handles special characters and spaces in filenames
- Content served as plain text with no caching
- Direct HDFS access ensures files are always available

**Note**: This feature is available for all newly processed files. Files processed before this feature was added will not have clickable links until they are reprocessed.

## File Processing Edge Cases

### URL Encoding Issues

**Issue**: File names with special characters may cause URL encoding/decoding problems.

**Symptoms**:
- Files with names like `[O'Reilly Technical Guide]` in URLs
- URL decoding failures in logs

**Resolution**:
- The application automatically handles URL decoding
- Special characters are properly escaped in file URIs

### Temporary File Cleanup

**Issue**: Temporary files may not be cleaned up if processing is interrupted.

**Prevention**:
- Application uses try-with-resources and finally blocks
- Temporary directories are cleaned up automatically
- Failed processing still triggers cleanup

## Error Handling Improvements

### Enhanced Error Categorization

The application now categorizes errors into specific types:

1. **CORRUPTED PDF**: PDF-specific corruption issues
2. **TIKA PARSING ERROR**: General Tika parsing problems
3. **FILE I/O ERROR**: File system or permission issues
4. **MEMORY ERROR**: Out-of-memory conditions
5. **UNKNOWN EXTRACTION ERROR**: Unexpected errors

Each category provides specific guidance and context in the logs.

### Graceful Degradation

- Failed file processing does not crash the application
- Empty results are returned for failed extractions
- Processing continues with remaining files
- Detailed error logging helps with debugging

## Best Practices

1. **Monitor Logs**: Watch for specific error categories to identify systemic issues
2. **File Validation**: Pre-validate files when possible
3. **Resource Management**: Monitor memory usage with large files
4. **Error Recovery**: Implement retry logic for transient failures
5. **Source Verification**: Verify file sources and integrity

## Troubleshooting Checklist

When encountering extraction failures:

1. Check the error category in logs
2. Verify file integrity and format
3. Monitor memory usage for large files
4. Check HDFS connectivity and permissions
5. Review file tracking status for retry issues

## Recent Fixes Summary

### Version 1.4.1 Fixes:
- ✅ **Memory Management**: Streaming extraction for large files (>50MB)
- ✅ **File Tracking**: Only mark files as processed after successful completion
- ✅ **HDFS Writing**: URL encoding for filenames with spaces/special characters
- ✅ **Retry Capability**: Failed files can now be retried after fixes
- ✅ **Memory Configuration**: Increased direct buffer memory limits

### Version 1.4.0 Fixes:
- ✅ **Chunking Logic**: Fixed token vs byte confusion in text splitting
- ✅ **UI Enhancement**: Clickable file links to view processed text
- ✅ **Error Categorization**: Enhanced error logging and categorization 