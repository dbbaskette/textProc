# TextProc Release Process Documentation

## Overview

The `release.sh` script is now a generic Maven project release script that automates the complete release process for any Maven project, including TextProc. The script automatically detects project details from `pom.xml` and handles versioning, building, and GitHub release creation.

**🔗 Source**: [Generic Maven Release Script Gist](https://gist.github.com/dbbaskette/e3c3b0c7ff90c715c6b11ca1e45bb3a6)

## Prerequisites

Before running the release script, ensure you have:

### Required Tools
- **Git**: Version control system
- **GitHub CLI (`gh`)**: For creating GitHub releases
  - Install: `brew install gh` (macOS) or [GitHub CLI installation guide](https://cli.github.com/manual/installation)
  - Authentication: `gh auth login`
- **Maven**: Build tool (prefer wrapper `./mvnw` over system `mvn`)
  - Maven wrapper should be present in project root
  - Fallback to system Maven if wrapper not available

### Required Files
- **`pom.xml`**: Maven project file in current directory (used for auto-detection)
- **`VERSION`**: Version file (created automatically if missing)
- **Git Repository**: Must be initialized with remote origin

### Permissions
- Write access to the Git repository
- Permission to create tags and releases on GitHub
- GitHub authentication via `gh` CLI

## Configuration

The generic script supports environment variable configuration:

| Variable | Default | Description |
|----------|---------|-------------|
| `VERSION_FILE` | `VERSION` | Name of version file to create/update |
| `SKIP_TESTS` | `true` | Skip tests during JAR build for faster releases |
| `DEFAULT_STARTING_VERSION` | `1.0.0` | Initial version if no version found |

### Examples
```bash
# Include tests in build
SKIP_TESTS=false ./release.sh

# Use custom version file  
VERSION_FILE=VERSION.txt ./release.sh

# Set custom starting version
DEFAULT_STARTING_VERSION=0.1.0 ./release.sh
```

## Release Process Steps

The script executes the following steps in order:

### 1. System Requirements Check
- Verifies Git is available
- Checks for GitHub CLI (`gh`) installation and authentication
- Ensures Maven or Maven wrapper is available
- Exits with error if any requirements are missing

### 2. Git Repository Validation
- Confirms current directory is a Git repository
- Fetches latest changes from remote
- Displays current branch information
- Warns about uncommitted changes and asks for confirmation

### 3. Project Detection & Version Management
- **Auto-detects project name** from `pom.xml` artifactId
- Reads current version from `VERSION` file or `pom.xml`
- Creates `VERSION` file with default version if neither exists
- Displays project name and current version information

### 4. Version Selection
Interactive menu with options:
- **Option 1**: Increment patch version (e.g., 1.0.0 → 1.0.1)
- **Option 2**: Increment minor version (e.g., 1.0.0 → 1.1.0)
- **Option 3**: Increment major version (e.g., 1.0.0 → 2.0.0)
- **Option 4**: Specify custom version (with validation)

### 5. File Updates
- Updates `VERSION` file with new version
- Updates `pom.xml` version using Maven versions plugin
- Rolls back changes if POM update fails

### 6. User Input Collection
- Prompts for commit message (defaults to "Release v{version}")
- Prompts for release notes (defaults to commit message)

### 7. Pre-Release Confirmation
- Shows all files to be committed
- Displays complete release process overview
- Asks for final confirmation before proceeding

### 8. Git Operations
- Commits all changes with specified message
- Pushes changes to remote repository

### 9. Tag Creation
- Creates annotated git tag `v{version}`
- Pushes tag to remote repository

### 10. JAR Build Process  
- Cleans previous builds
- Builds JAR using `mvn clean package` (with optional test skipping)
- Locates built JAR at `target/{project-name}-{version}.jar`
- Reports file size and success status

### 11. GitHub Release Creation
- Creates GitHub release with tag `v{version}`
- Attaches built JAR file to release
- Falls back to release without JAR if build failed
- Includes release notes and proper formatting

## Usage

### Basic Usage
```bash
./release.sh
```

### Interactive Flow
1. Run the script
2. Choose version increment option (1-4)
3. Enter commit message (or use default)
4. Enter release notes (or use default)
5. Review files to be committed
6. Confirm release process
7. Wait for completion

### Example Session
```
=== Release Script ===

[INFO] Fetching latest changes from remote...
[INFO] Current branch: main
[INFO] Current version: 1.0.5

Version options:
1) Increment patch version (1.0.5 -> 1.0.6)
2) Increment minor version (1.0.5 -> 1.1.0)
3) Increment major version (1.0.5 -> 2.0.0)
4) Specify custom version

Choose an option [1-4, default: 1]: 1

[INFO] New version will be: 1.0.6
[INFO] Updating POM version to 1.0.6...
[SUCCESS] POM version updated successfully

Enter release commit message [Release v1.0.6]: 
Enter release notes (optional): Fixed PDF corruption handling

[INFO] Files to be committed:
M VERSION
M pom.xml
M src/main/java/com/baskettecase/textProc/service/ExtractionService.java

[INFO] Release process will:
  1. Update POM version to 1.0.6
  2. Commit and push changes
  3. Create and push git tag v1.0.6
  4. Build JAR file using Maven
  5. Create GitHub release with JAR attachment

Proceed with release? (y/N): y
```

## Error Handling

### Common Issues and Solutions

#### Missing Tools
```
[ERROR] Missing required tools: gh (GitHub CLI)
```
**Solution**: Install GitHub CLI and authenticate

#### Git Repository Issues
```
[ERROR] Not in a git repository
```
**Solution**: Run script from project root directory

#### POM Update Failures
```
[ERROR] Failed to update POM version
```
**Solution**: Verify `pom.xml` exists and Maven is working

#### JAR Build Failures
```
[ERROR] Failed to build JAR
```
**Solution**: Check Maven configuration and dependencies

#### GitHub Release Failures
```
[WARNING] Failed to create GitHub release
```
**Solution**: Verify GitHub CLI authentication and repository permissions

### Rollback Behavior
- If cancelled during confirmation, restores original VERSION and POM
- If POM update fails, restores original VERSION file
- Script exits cleanly on any critical failure

## Configuration

### Version File (`VERSION`)
- Simple text file containing semantic version
- Created automatically if missing
- Updated on each release

### Maven Configuration
- Uses `./mvnw` wrapper if available
- Falls back to system `mvn` command
- Requires Maven versions plugin for POM updates

### GitHub Integration
- Uses `gh` CLI for release creation
- Requires authentication: `gh auth login`
- Creates releases with proper tags and attachments

## Security Considerations

### Permissions Required
- Git repository write access
- GitHub repository admin or maintainer role
- Local file system write permissions

### Best Practices
- Run from clean working directory
- Review all changes before confirmation
- Ensure tests pass before release
- Use semantic versioning consistently

## Troubleshooting

### Debug Information
The script provides detailed logging:
- `[INFO]`: General information
- `[SUCCESS]`: Successful operations
- `[WARNING]`: Non-critical issues
- `[ERROR]`: Critical failures

### Common Workflows

#### Major Release
1. Choose option 3 (major version)
2. Add comprehensive release notes
3. Verify all breaking changes are documented

#### Patch Release
1. Choose option 1 (patch version)
2. Focus on bug fixes in release notes
3. Ensure backward compatibility

#### Hotfix Release
1. Create from appropriate branch
2. Use patch version increment
3. Fast-track testing and deployment

## Integration with CI/CD

The release script can be integrated into CI/CD pipelines:

### GitHub Actions Example
```yaml
- name: Create Release
  run: ./release.sh
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Manual Release Process
1. Ensure all changes are committed
2. Run comprehensive tests
3. Update documentation
4. Execute release script
5. Verify release artifacts
6. Announce release

## File Structure After Release

```
textProc/
├── VERSION              # Updated version
├── pom.xml             # Updated POM version
├── target/
│   └── textProc-{version}.jar  # Built JAR
├── release.sh          # Release script
└── docs/
    └── release-process.md  # This documentation
```

## Version History

- **v1.0.0**: Initial release script
- **v1.1.0**: Added JAR build and GitHub release integration
- **v1.2.0**: Enhanced error handling and documentation
- **Current**: Comprehensive Maven integration and rollback support 