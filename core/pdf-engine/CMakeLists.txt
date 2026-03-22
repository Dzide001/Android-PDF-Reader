cmake_minimum_required(VERSION 3.18)

project(mupdf_engine)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_package(JNI REQUIRED)

# Add MuPDF library (external)
# Note: MuPDF should be built separately and linked
# This is a placeholder for the JNI wrapper

# Source files
set(SOURCES
    src/main/cpp/jni_bridge.cpp
    src/main/cpp/mupdf_wrapper.cpp
)

# Include directories
include_directories(
    ${JNI_INCLUDE_DIRS}
    src/main/cpp/include
)

# Create shared library
add_library(mupdf_engine SHARED ${SOURCES})

# Link JNI and MuPDF libraries
target_link_libraries(mupdf_engine
    ${JNI_LIBRARIES}
    # MuPDF library will be linked here
    # mupdf
)
