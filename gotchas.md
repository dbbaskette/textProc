# Gotchas and Solutions

This document tracks issues encountered during development and their solutions.

## RabbitMQ Connection Issues

### Problem: RabbitMQ Connection Refused Locally
**Issue**: Application fails to start locally with SCDF profile due to RabbitMQ connection errors

**Root Cause**: Application tries to connect to `localhost:5672` but no RabbitMQ server is running locally

**Solution**: 
- Added RabbitMQ configuration in `application-scdf.properties` with environment variable fallbacks
- Created `LocalDevelopmentConfig` to provide warnings when running locally
- Added connection timeout settings to prevent long startup delays

**For Local Development**:
1. **Option 1**: Use `standalone` profile (no RabbitMQ needed)
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=standalone
   ```

2. **Option 2**: Start RabbitMQ locally
   ```bash
   # Using Docker
   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management
   ```

3. **Option 3**: Run SCDF profile (web interface works, processing doesn't)
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=scdf
   ```

**For Cloud Foundry Deployment**: RabbitMQ connection is automatically configured via service binding

**Verification**: 
- Local development: Check startup logs for RabbitMQ warnings
- Cloud Foundry: Check logs for successful RabbitMQ connection

## UI Issues

### Problem: Processed Text Not Displaying
**Issue**: Clicking on processed files in UI shows empty content

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

### Problem: Excessive Log Volume
**Issue**: Application generating too many log entries, hitting Cloud Foundry limits

**Root Cause**: DEBUG level logging and verbose diagnostic messages

**Solution**: 
- Reduced default logging level from DEBUG to INFO
- Moved diagnostic messages to DEBUG level
- Reduced verbose message logging

**Verification**: Check logs for reduced volume and no log limit warnings

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
- **Heap Size**: 2GB maximum, 1GB minimum
- **Direct Buffer Memory**: 512MB
- **Cloud Foundry Memory**: 1GB allocated

### RabbitMQ Configuration
- **Local Development**: `localhost:5672` (with warnings)
- **Cloud Foundry**: Auto-configured via service binding
- **Connection Timeout**: 5 seconds
- **Heartbeat**: 30 seconds

### Processing Controls
- **Default State**: STOPPED (for demo control)
- **Consumer Management**: Pause/resume RabbitMQ consumers
- **Reset Functionality**: Clear processed files from HDFS
- **UI Integration**: Real-time status display and controls 