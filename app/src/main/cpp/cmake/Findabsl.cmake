# Findabsl.cmake shim for Android NDK build.
# aasdk's parent CMakeLists calls find_package(absl REQUIRED) after
# protobuf/CMakeLists.txt built abseil via FetchContent.
# FetchContent targets are already in scope — just mark found.

# Check if abseil targets exist from FetchContent
if(TARGET absl::base OR TARGET absl::strings)
    set(absl_FOUND TRUE)
    message(STATUS "Findabsl shim: abseil targets available from FetchContent")
else()
    # Abseil not yet built — this shouldn't happen if protobuf subdirectory ran first
    message(FATAL_ERROR "Findabsl shim: abseil targets not found — protobuf FetchContent must run first")
endif()
