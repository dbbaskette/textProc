# Text Processing Application - Gotchas and Solutions

This document tracks issues encountered during development and their solutions.

## Memory Management Issues

### Problem: OutOfMemoryError with Large Files
**Issue**: Large PDF files (>50MB) cause `OutOfMemoryError: Cannot reserve X bytes of direct buffer memory`

**Root Cause**: Default JVM direct buffer memory limit (10MB) is insufficient for large files

**Solution**: 
- Set JVM memory options in SCDF deployment properties:
  ```
  deployer.textProc.environment-variables=JAVA_OPTS=-XX:MaxDirectMemorySize=512m -Xmx2g -Xms1g
  ```
- Use streaming extraction for files >50MB
- Monitor memory usage in application logs

**Verification**: Check logs for `JVM Memory Configuration` showing increased limits

## File Processing Issues

### Problem: Files Not Appearing in Processed List
**Issue**: Files processed but not showing in UI

**Root Cause**: In-memory tracking marks files as processed before completion

**Solution**: Modified tracking logic to mark files as processed only after successful completion

**Verification**: Check logs for "Successfully processed and tracked file" messages

### Problem: HDFS Writing Failures
**Issue**: HTTP 400 errors when writing files to HDFS

**Root Cause**: Spaces in filenames not URL-encoded

**Solution**: URL-encode filenames before writing to HDFS

**Verification**: Check logs for successful HDFS write operations

### Problem: No Text Extracted from Some Files
**Issue**: "No text was extracted" warnings for certain files

**Root Cause**: Image-based PDFs or corrupted files

**Solution**: 
- Files are still processed and tracked
- Check if files contain actual text content
- Consider OCR for image-based PDFs if needed

**Verification**: Check logs for extraction success/failure messages

## UI and Display Issues

### Problem: UI Links Point to Local Files Instead of HDFS
**Issue**: Processed text links point to local temp files

**Root Cause**: Controller serving from local temp files instead of HDFS

**Solution**: Updated controller to serve processed text directly from HDFS via WebHDFS REST API

**Verification**: Check that UI links work and display content from HDFS

### Problem: HDFS URLs Hardcoded
**Issue**: HDFS URLs hardcoded in controller

**Root Cause**: URLs not configurable through application properties

**Solution**: Made HDFS URLs configurable through application properties:
  ```
  app.hdfs.base-url=http://35.196.56.130:9870/webhdfs/v1
  app.hdfs.processed-files-path=/processed_files
  ```

**Verification**: URLs can be changed via application properties without code changes

## Configuration Issues

### Problem: JVM Settings Not Applied
**Issue**: JVM memory settings not taking effect in Cloud Foundry

**Root Cause**: Wrong environment variable name in SCDF deployment properties

**Solution**: Use `JAVA_OPTS` instead of `java.opts`:
  ```
  deployer.textProc.environment-variables=JAVA_OPTS=-XX:MaxDirectMemorySize=512m -Xmx2g -Xms1g
  ```

**Verification**: Check startup logs for `JVM Memory Configuration` showing correct limits

### Problem: SCDF Deployment Properties Not Applied
**Issue**: Deployment properties not reaching the application

**Root Cause**: Properties not properly formatted or stream not redeployed

**Solution**: 
- Ensure correct property format
- Redeploy stream after property changes
- Verify environment variables in CF app

**Verification**: Check `cf env` output for applied environment variables

## Performance Optimizations

### Problem: Large File Processing Slow
**Issue**: Large files take too long to process

**Root Cause**: Loading entire file into memory

**Solution**: Implemented streaming extraction for files >50MB

**Verification**: Check logs for "using streaming extraction" messages

### Problem: Memory Usage High
**Issue**: Application using too much memory

**Root Cause**: Insufficient memory allocation

**Solution**: Increased heap and direct buffer memory allocation

**Verification**: Monitor memory usage in CF app metrics

## Release and Deployment Issues

### Problem: Release Script Failures
**Issue**: GitHub release creation failing

**Root Cause**: Network issues or GitHub CLI problems

**Solution**: 
- Added retry logic to release script
- Improved error handling
- Added timeout commands for compatibility

**Verification**: Check release script output for success messages

### Problem: JAR Upload Failures
**Issue**: JAR files not uploading to GitHub releases

**Root Cause**: File size or network issues

**Solution**: 
- Added file existence checks
- Improved error messages
- Added retry logic

**Verification**: Check GitHub releases for uploaded JAR files

## UI Enhancement Issues

### Problem: Basic UI Not User-Friendly
**Issue**: Simple HTML interface not providing good user experience

**Root Cause**: Basic Bootstrap template without modern features

**Solution**: Created modern dashboard with:
- Real-time statistics cards
- Auto-refresh functionality
- Status distribution charts
- Enhanced file display
- Responsive design

**Verification**: Check new dashboard features and functionality

## Current Configuration

### HDFS Configuration
- **Base URL**: Configurable via `app.hdfs.base-url` property
- **Processed Files Path**: Configurable via `app.hdfs.processed-files-path` property
- **Default Values**: 
  - Base URL: `http://35.196.56.130:9870/webhdfs/v1`
  - Processed Files Path: `/processed_files`

### Memory Configuration
- **Direct Buffer Memory**: 512MB (configurable via `JAVA_OPTS`)
- **Heap Size**: 2GB max, 1GB initial (configurable via `JAVA_OPTS`)
- **Default**: 10MB direct buffer, 1GB heap (if not configured)

### File Processing Configuration
- **Chunk Size**: 8KB (configurable)
- **Max File Size**: 100MB (configurable)
- **Streaming Threshold**: 50MB (files larger than this use streaming)

## Best Practices

1. **Always configure JVM memory settings** in SCDF deployment properties
2. **Monitor application logs** for memory errors and processing status
3. **Use streaming extraction** for files >50MB
4. **Redeploy streams** after configuration changes
5. **Verify environment variables** are applied correctly
6. **Test with different file sizes** to ensure proper memory allocation
7. **Monitor HDFS connectivity** and file permissions
8. **Use configurable properties** instead of hardcoded values

## Troubleshooting Checklist

- [ ] Check JVM memory settings in startup logs
- [ ] Verify HDFS connectivity and file permissions
- [ ] Monitor memory usage in CF app metrics
- [ ] Check for memory errors in application logs
- [ ] Verify file processing status in logs
- [ ] Test UI functionality and auto-refresh
- [ ] Confirm configuration properties are applied
- [ ] Check release script execution and GitHub uploads 