
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("MWFonofoneModel")

# ${ANDROID_ABI} is handy for our purpose here. Probably this ${ANDROID_ABI} is
# the most valuable thing of this sample, the rest are pretty much normal cmake
add_library(sndfile SHARED IMPORTED)
set_target_properties(sndfile PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libsndfile/lib/${CMAKE_BUILD_TYPE}/${ANDROID_ABI}/libsndfile.so)

add_library(samplerate STATIC IMPORTED)
set_target_properties(samplerate PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libsamplerate/lib/${CMAKE_BUILD_TYPE}/${ANDROID_ABI}/libsamplerate.a)

# build application's shared lib
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        fileio

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${CMAKE_CURRENT_SOURCE_DIR}/fileio.cpp )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log )

target_include_directories(fileio PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libsndfile/include
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libsamplerate/include)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        fileio
        sndfile
        samplerate
        android
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib} )
