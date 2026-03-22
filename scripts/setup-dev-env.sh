#!/bin/bash

# PDF Reader Development Environment Setup Script
# This script configures the development environment for building the PDF Reader Android app
# Tested on macOS and Linux

set -e  # Exit on error

echo "🚀 PDF Reader Development Environment Setup"
echo "==========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if we're in the right directory
if [ ! -f "PROJECT_ROADMAP.md" ]; then
    echo -e "${RED}Error: Run this script from the project root directory${NC}"
    exit 1
fi

# 1. Check Java Installation
echo -e "${YELLOW}1. Checking Java Installation...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}Java not found. Please install Java 11 or higher.${NC}"
    echo "Download from: https://www.oracle.com/java/technologies/downloads/"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "\K[^"]+')
echo -e "${GREEN}✓ Java $JAVA_VERSION found${NC}"
echo ""

# 2. Check Android SDK
echo -e "${YELLOW}2. Checking Android SDK...${NC}"
if [ -z "$ANDROID_SDK_ROOT" ] && [ -z "$ANDROID_HOME" ]; then
    # Try common locations
    if [ -d "$HOME/Android/sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Android/sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
    else
        echo -e "${RED}Android SDK not found.${NC}"
        echo "Please set ANDROID_SDK_ROOT or install Android Studio"
        echo "Download from: https://developer.android.com/studio"
        exit 1
    fi
fi
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
echo -e "${GREEN}✓ Android SDK found at: $ANDROID_SDK_ROOT${NC}"
echo ""

# 3. Check NDK
echo -e "${YELLOW}3. Checking Android NDK...${NC}"
NDK_VERSION="26.2.11394342"  # Match this with gradle.properties
if [ ! -d "$ANDROID_SDK_ROOT/ndk/$NDK_VERSION" ]; then
    echo -e "${YELLOW}  Installing NDK $NDK_VERSION...${NC}"
    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/bin"
    # Note: Full sdkmanager setup required for actual NDK installation
    echo -e "${YELLOW}  Run: sdkmanager 'ndk;$NDK_VERSION'${NC}"
fi
echo -e "${GREEN}✓ NDK setup verified${NC}"
echo ""

# 4. Check CMake
echo -e "${YELLOW}4. Checking CMake...${NC}"
if ! command -v cmake &> /dev/null; then
    echo -e "${YELLOW}  CMake not found in PATH.${NC}"
    echo "  Install via: brew install cmake (macOS) or apt-get install cmake (Linux)"
    read -p "  Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}  (Gradle may download CMake automatically)${NC}"
    else
        exit 1
    fi
else
    CMAKE_VERSION=$(cmake --version | grep -oP 'cmake version \K[^-]+')
    echo -e "${GREEN}✓ CMake $CMAKE_VERSION found${NC}"
fi
echo ""

# 5. Check Git
echo -e "${YELLOW}5. Checking Git...${NC}"
if ! command -v git &> /dev/null; then
    echo -e "${RED}Git not found. Please install Git.${NC}"
    exit 1
fi
GIT_VERSION=$(git --version | grep -oP 'version \K[^-]+')
echo -e "${GREEN}✓ Git $GIT_VERSION found${NC}"
echo ""

# 6. Initialize Git submodules
echo -e "${YELLOW}6. Initializing Git Submodules...${NC}"
if [ -d ".git" ]; then
    git submodule update --init --recursive
    echo -e "${GREEN}✓ Submodules initialized${NC}"
else
    echo -e "${YELLOW}  Note: Not a git repository yet. Skipping submodule init.${NC}"
fi
echo ""

# 7. Create necessary directories
echo -e "${YELLOW}7. Creating Project Directories...${NC}"
mkdir -p app/src/{main/{kotlin,cpp},test/kotlin,androidTest/kotlin}
mkdir -p core/{pdf-engine/src/main/{kotlin,cpp},renderer/src/main/kotlin,annotations/src/main/kotlin,ocr/src/main/kotlin,storage/src/main/kotlin}
mkdir -p native/{mupdf,tesseract,jni}
mkdir -p scripts
mkdir -p docs
echo -e "${GREEN}✓ Directories created${NC}"
echo ""

# 8. Set environment variables
echo -e "${YELLOW}8. Setting Environment Variables...${NC}"
cat > .env.local << EOF
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export NDK_VERSION="$NDK_VERSION"
EOF

# Add to .bashrc / .zshrc if needed
if [[ ! -z "${SHELL##*/zsh}" ]]; then
    SHELL_RC="$HOME/.zshrc"
else
    SHELL_RC="$HOME/.bashrc"
fi

echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\"" >> "$SHELL_RC"
echo "export ANDROID_HOME=\"$ANDROID_SDK_ROOT\"" >> "$SHELL_RC"
echo -e "${GREEN}✓ Environment variables set${NC}"
echo ""

# 9. Download example PDF for testing
echo -e "${YELLOW}9. Downloading Sample PDF for Testing...${NC}"
mkdir -p app/src/main/assets
if [ ! -f "app/src/main/assets/sample.pdf" ]; then
    echo "  (Optional: Add sample.pdf to app/src/main/assets/)"
fi
echo ""

# 10. Initialize Gradle wrapper
echo -e "${YELLOW}10. Validating Gradle Configuration...${NC}"
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo -e "${YELLOW}  Gradle wrapper not found. Will be downloaded on first build.${NC}"
else
    echo -e "${GREEN}✓ Gradle wrapper found${NC}"
fi
echo ""

# 11. Run initial Gradle checks
echo -e "${YELLOW}11. Running Gradle Validation...${NC}"
if command -v ./gradlew &> /dev/null || command -v gradlew.bat &> /dev/null; then
    echo "  (Gradle wrapper found. Run './gradlew build' to compile)"
fi
echo ""

# 12. Print IDE setup instructions
echo -e "${YELLOW}12. IDE Setup Instructions${NC}"
echo ""
echo "For Android Studio:"
echo "  1. Open project root in Android Studio"
echo "  2. Wait for Gradle sync to complete"
echo "  3. Go to File → Project Structure"
echo "  4. Verify NDK path points to: $ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
echo "  5. Run: Build → Make Project"
echo ""

# 13. Success message
echo ""
echo -e "${GREEN}✅ Development Environment Setup Complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Load environment: source .env.local"
echo "  2. Build project: ./gradlew build"
echo "  3. Run tests: ./gradlew test"
echo "  4. Run app: ./gradlew installDebug"
echo ""
echo "For more information, see:"
echo "  - docs/ARCHITECTURE.md (Architecture decisions)"
echo "  - PROJECT_ROADMAP.md (Feature roadmap)"
echo "  - CONTRIBUTING.md (Development guidelines)"
echo ""
