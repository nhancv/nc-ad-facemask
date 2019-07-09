# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
add_library( # Sets the name of the library.
        CGE
        # Sets the library as a shared library.
        SHARED
        # Provides a relative path to your source file(s).
        cge/common/cgeCommonDefine.cpp
        cge/common/cgeGLFunctions.cpp
        cge/common/cgeImageFilter.cpp
        cge/common/cgeImageHandler.cpp
        cge/common/cgeShaderFunctions.cpp
        cge/common/cgeGlobal.cpp
        cge/common/cgeTextureUtils.cpp

        cge/filters/cgeAdvancedEffects.cpp
        cge/filters/cgeAdvancedEffectsCommon.cpp
        cge/filters/cgeBilateralBlurFilter.cpp
        cge/filters/cgeMosaicBlurFilter.cpp
        cge/filters/cgeBeautifyFilter.cpp

        cge/filters/cgeBrightnessAdjust.cpp
        cge/filters/cgeColorLevelAdjust.cpp
        cge/filters/cgeContrastAdjust.cpp
        cge/filters/cgeCurveAdjust.cpp
        cge/filters/cgeExposureAdjust.cpp
        cge/filters/cgeFilterBasic.cpp
        cge/filters/cgeHueAdjust.cpp
        cge/filters/cgeMonochromeAdjust.cpp
        cge/filters/cgeSaturationAdjust.cpp
        cge/filters/cgeSelectiveColorAdjust.cpp
        cge/filters/cgeShadowHighlightAdjust.cpp
        cge/filters/cgeSharpenBlurAdjust.cpp
        cge/filters/cgeTiltshiftAdjust.cpp
        cge/filters/cgeVignetteAdjust.cpp
        cge/filters/cgeWhiteBalanceAdjust.cpp
        cge/filters/cgeColorBalanceAdjust.cpp
        cge/filters/cgeLookupFilter.cpp

        cge/filters/cgeBlendFilter.cpp

        cge/filters/cgeDataParsingEngine.cpp
        cge/filters/cgeMultipleEffects.cpp
        cge/filters/cgeMultipleEffectsCommon.cpp

        cge/filters/cgeHazeFilter.cpp
        cge/filters/cgePolarPixellateFilter.cpp
        cge/filters/cgePolkaDotFilter.cpp
        cge/filters/cgeHalftoneFilter.cpp
        cge/filters/cgeEdgeFilter.cpp
        cge/filters/cgeEmbossFilter.cpp
        cge/filters/cgeCrosshatchFilter.cpp
        cge/filters/CGELiquifyFilter.cpp
        cge/filters/cgeRandomBlurFilter.cpp
        cge/filters/cgeMinValueFilter.cpp
        cge/filters/cgeMaxValueFilter.cpp
        cge/filters/cgeSketchFilter.cpp
        cge/filters/cgeLerpblurFilter.cpp

        cge/filters/cgeDynamicFilters.cpp
        cge/filters/cgeDynamicWaveFilter.cpp
        cge/filters/cgeMotionFlowFilter.cpp
        cge/filters/cgeColorMappingFilter.cpp
        cge/extends/cgeThread.cpp

        interface/cgeDeformFilterWrapper.cpp
        interface/cgeFFmpegNativeLibrary.cpp
        interface/cgeFrameRecorder.cpp
        interface/cgeFrameRecorderWrapper.cpp
        interface/cgeFrameRenderer.cpp
        interface/cgeFrameRendererWrapper.cpp
        interface/cgeImageHandlerAndroid.cpp
        interface/cgeImageHandlerWrapper.cpp
        interface/cgeNativeLibrary.cpp
        interface/cgeSharedGLContext.cpp
        interface/cgeUtilFunctions.cpp
        interface/cgeVideoDecoder.cpp
        interface/cgeVideoEncoder.cpp
        interface/cgeVideoPlayer.cpp

        source/cgeCustomFilters.cpp
        source/cgeCustomFilterWrapper.cpp
        source/cgeVideoUtils.cpp
        source/customFilter_0.cpp
        source/customFilter_N.cpp
        source/customHelper.cpp

        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        CGE

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        android
        EGL
        GLESv2
        jnigraphics
        atomic
        )