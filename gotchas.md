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

### Large PDF Memory Issues

**Issue**: Very large PDF files may cause out-of-memory errors during processing.

**Symptoms**:
- OutOfMemoryError exceptions
- Application becomes unresponsive
- Error messages containing "memory" or "heap space"

**Resolution**:
1. Increase JVM heap size: `-Xmx4g` or higher
2. Process files in smaller chunks
3. Consider streaming processing for very large files

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
2. Verify file accessibility and permissions
3. Test file with external PDF tools
4. Check available memory and system resources
5. Review temporary directory space
6. Validate source URL or file path
7. Consider file size limitations

## Release Script Issues

### Maven Output Contamination

**Issue**: The release script was failing to attach JAR files to GitHub releases, despite successfully building them.

**Symptoms**:
- Script shows: `[SUCCESS] JAR built successfully: target/textProc-X.X.X.jar`
- But then shows: `[WARNING] No JAR file available. Creating release without JAR attachment...`
- JAR file exists in target directory but script can't find it

**Root Cause**: 
- Maven command output was being captured by command substitution `$(build_jar ...)`
- Maven outputs all build logs to stdout, contaminating the function return value
- Function was returning thousands of lines of Maven output instead of just the JAR path

**Resolution**:
1. **Redirect Maven output to stderr**: Changed `if $build_cmd;` to `if $build_cmd >&2;`
2. **Added debug output**: Shows available JAR files when expected file not found
3. **Improved error handling**: Better diagnostics for JAR file detection

**Prevention**:
- Always redirect command output to stderr when using command substitution for return values
- Test functions in isolation before integrating into larger scripts
- Use proper output redirection: `>&2` for logs, stdout for return values

### Version Synchronization Issues

**Issue**: POM version and VERSION file can get out of sync, causing build failures.

**Symptoms**:
- Script builds JAR with different version than expected
- File not found errors for JAR files

**Resolution**:
- Ensure POM version is updated before building JAR
- The release script handles this automatically via `update_pom_version` function

### GitHub CLI Upload Timeouts

**Issue**: Large JAR files (>100MB) may timeout during upload to GitHub releases.

**Symptoms**:
- `Post "https://api.github.com/graphql": net/http: TLS handshake timeout`
- JAR built successfully but fails to attach to GitHub release
- Release created without JAR attachment

**Root Cause**: 
- Default GitHub CLI timeout is too short for large files
- Network connectivity issues during upload
- GitHub API rate limiting or server issues

**Resolution**:
1. **Automatic retry logic**: Script now retries JAR upload up to 3 times
2. **Extended timeouts**: GitHub CLI timeout increased to 5 minutes (300s)
3. **Graceful fallback**: Creates release without JAR if all uploads fail
4. **Manual upload option**: Provides commands for manual JAR attachment

**Configuration**:
- `UPLOAD_RETRY_COUNT`: Number of retry attempts (default: 3)
- `UPLOAD_TIMEOUT`: Timeout in seconds (default: 300)
- Example: `UPLOAD_TIMEOUT=600 UPLOAD_RETRY_COUNT=5 ./release.sh`

**Manual Recovery**:
If automatic upload fails, manually attach JAR:
```bash
gh release upload v1.0.0 target/project-1.0.0.jar
```

## Version History

- v1.1.6: Added retry logic and timeout handling for GitHub CLI JAR uploads
- v1.1.5: Tested and verified JAR path fix works correctly  
- v1.1.4: Created new gist with fixed release script (https://gist.github.com/dbbaskette/e3c3b0c7ff90c715c6b11ca1e45bb3a6)
- v1.1.3: Fixed critical bug in release script where Maven output was contaminating JAR path variable  
- v0.0.11: Enhanced error handling and categorization for PDF corruption issues 