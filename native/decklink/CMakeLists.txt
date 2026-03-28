cmake_minimum_required(VERSION 3.15)
project(decklink_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------- JNI ----------
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# ---------- DeckLink SDK ----------
# Set DECKLINK_SDK_DIR to the path containing DeckLinkAPI IDL/header files.
# On Windows: path to the SDK's Win/include directory (contains .idl files)
# On macOS:   "/Library/Frameworks/DeckLinkAPI.framework/Headers"
if(NOT DEFINED DECKLINK_SDK_DIR)
    if(WIN32)
        message(FATAL_ERROR "DECKLINK_SDK_DIR not set. Pass -DDECKLINK_SDK_DIR=\"C:/path/to/Blackmagic-DeckLink-SDK/Win/include\"")
    elseif(APPLE)
        set(DECKLINK_SDK_DIR "/Library/Frameworks/DeckLinkAPI.framework/Headers")
    else()
        set(DECKLINK_SDK_DIR "/usr/include")
    endif()
endif()

if(EXISTS "${DECKLINK_SDK_DIR}")
    message(STATUS "DeckLink SDK: ${DECKLINK_SDK_DIR}")
else()
    message(FATAL_ERROR "DeckLink SDK not found at ${DECKLINK_SDK_DIR} - set -DDECKLINK_SDK_DIR=...")
endif()

# ---------- Windows: generate headers from IDL using MIDL ----------
if(WIN32)
    set(MIDL_OUTPUT_DIR "${CMAKE_BINARY_DIR}/generated")
    file(MAKE_DIRECTORY "${MIDL_OUTPUT_DIR}")

    # Run MIDL to generate DeckLinkAPI_h.h and DeckLinkAPI_i.c from DeckLinkAPI.idl
    add_custom_command(
        OUTPUT "${MIDL_OUTPUT_DIR}/DeckLinkAPI_h.h"
               "${MIDL_OUTPUT_DIR}/DeckLinkAPI_i.c"
        COMMAND midl
            /h "DeckLinkAPI_h.h"
            /iid "DeckLinkAPI_i.c"
            /proxy "DeckLinkAPI_p.c"
            /dlldata "dlldata.c"
            /env x64
            /W1 /char signed /ms_ext /c_ext
            /out "${MIDL_OUTPUT_DIR}"
            "${DECKLINK_SDK_DIR}/DeckLinkAPI.idl"
        DEPENDS "${DECKLINK_SDK_DIR}/DeckLinkAPI.idl"
        COMMENT "Generating DeckLink COM headers from IDL..."
        VERBATIM
    )
    add_custom_target(decklink_idl DEPENDS
        "${MIDL_OUTPUT_DIR}/DeckLinkAPI_h.h"
        "${MIDL_OUTPUT_DIR}/DeckLinkAPI_i.c"
    )

    include_directories("${MIDL_OUTPUT_DIR}")
    include_directories("${DECKLINK_SDK_DIR}")
else()
    include_directories("${DECKLINK_SDK_DIR}")
endif()

# ---------- Detect DeckLink framework (macOS) ----------
if(APPLE)
    find_library(DECKLINK_FRAMEWORK DeckLinkAPI PATHS /Library/Frameworks)
endif()

# ---------- Sources ----------
set(SOURCES decklink_jni.cpp)

if(WIN32)
    list(APPEND SOURCES "${MIDL_OUTPUT_DIR}/DeckLinkAPI_i.c")
elseif(APPLE AND NOT DECKLINK_FRAMEWORK)
    # When the DeckLink framework is not installed (SDK-only build),
    # compile the dispatch source which provides CreateDeckLinkIteratorInstance
    # via runtime dynamic loading (dlopen).
    if(EXISTS "${DECKLINK_SDK_DIR}/DeckLinkAPIDispatch.cpp")
        list(APPEND SOURCES "${DECKLINK_SDK_DIR}/DeckLinkAPIDispatch.cpp")
        message(STATUS "Using DeckLinkAPIDispatch.cpp for runtime linking (no framework)")
    endif()
else()
    # Linux: compile the dispatch source which provides CreateDeckLinkIteratorInstance
    # via runtime dynamic loading (dlopen).
    if(EXISTS "${DECKLINK_SDK_DIR}/DeckLinkAPIDispatch.cpp")
        list(APPEND SOURCES "${DECKLINK_SDK_DIR}/DeckLinkAPIDispatch.cpp")
        message(STATUS "Using DeckLinkAPIDispatch.cpp for runtime linking (Linux)")
    endif()
endif()

add_library(decklink_jni SHARED ${SOURCES})

if(WIN32)
    add_dependencies(decklink_jni decklink_idl)
endif()

# ---------- Platform linking ----------
if(WIN32)
    target_link_libraries(decklink_jni PRIVATE ole32 oleaut32)
    target_compile_definitions(decklink_jni PRIVATE _WIN32_WINNT=0x0601)
elseif(APPLE)
    find_library(COREFOUNDATION_LIB CoreFoundation REQUIRED)
    target_link_libraries(decklink_jni PRIVATE ${COREFOUNDATION_LIB})
    if(DECKLINK_FRAMEWORK)
        target_link_libraries(decklink_jni PRIVATE ${DECKLINK_FRAMEWORK})
    endif()
else()
    # Linux: needs libdl for DeckLinkAPIDispatch.cpp (dlopen/dlsym)
    target_link_libraries(decklink_jni PRIVATE dl)
endif()

# ---------- Output ----------
set_target_properties(decklink_jni PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
)
