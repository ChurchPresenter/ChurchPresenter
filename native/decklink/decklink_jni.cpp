/**
 * DeckLink JNI native library for ChurchPresenter.
 *
 * Wraps the BlackMagic DeckLink SDK to provide video output
 * through DeckLink capture/playback cards via JNI.
 *
 * Build requirements:
 *   - BlackMagic DeckLink SDK (free download from blackmagicdesign.com)
 *   - BlackMagic Desktop Video drivers installed
 *   - JDK with JNI headers
 *   - CMake 3.15+
 *
 * Windows: uses COM (CoInitializeEx / CoCreateInstance)
 * macOS:   uses CoreFoundation / DeckLinkAPI.framework
 */

#include "org_churchpresenter_app_churchpresenter_composables_DeckLinkManager.h"

#include <cstring>
#include <string>
#include <vector>
#include <mutex>

// ── Platform-specific DeckLink SDK includes ─────────────────────────

#ifdef _WIN32
    #define WIN32_LEAN_AND_MEAN
    #include <windows.h>
    #include <comdef.h>
    #include "DeckLinkAPI_h.h"
    // DeckLinkAPI_i.c provides CLSID/IID definitions (compiled separately)

    // Helper: convert BSTR to std::string
    static std::string bstrToString(BSTR bstr) {
        if (!bstr) return "";
        int len = SysStringLen(bstr);
        int size = WideCharToMultiByte(CP_UTF8, 0, bstr, len, nullptr, 0, nullptr, nullptr);
        std::string result(size, 0);
        WideCharToMultiByte(CP_UTF8, 0, bstr, len, &result[0], size, nullptr, nullptr);
        return result;
    }

    // Ensure COM is initialized on the calling thread
    static void ensureCOM() {
        static thread_local bool initialized = false;
        if (!initialized) {
            CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            initialized = true;
        }
    }

#elif defined(__APPLE__)
    #include "DeckLinkAPI.h"

    // Helper: convert CFString to std::string
    static std::string cfStringToString(CFStringRef cfStr) {
        if (!cfStr) return "";
        CFIndex len = CFStringGetLength(cfStr);
        CFIndex maxSize = CFStringGetMaximumSizeForEncoding(len, kCFStringEncodingUTF8) + 1;
        std::string result(maxSize, 0);
        if (CFStringGetCString(cfStr, &result[0], maxSize, kCFStringEncodingUTF8)) {
            result.resize(std::strlen(result.c_str()));
        } else {
            result.clear();
        }
        return result;
    }

#else
    #include "DeckLinkAPI.h"
    // Linux: DeckLink SDK uses COM-like interfaces with AddRef/Release
#endif


// ── Global state ────────────────────────────────────────────────────

static std::mutex g_mutex;
static IDeckLinkOutput* g_output = nullptr;
static IDeckLink* g_device = nullptr;
static bool g_playbackStarted = false;
static long g_frameCount = 0;
static double g_fps = 30.0;
static BMDTimeScale g_timeScale = 30000;
static BMDTimeValue g_frameDuration = 1001;


// ── Helper: find display mode matching resolution ───────────────────

static IDeckLinkDisplayMode* findDisplayMode(IDeckLinkOutput* output, int width, int height) {
    IDeckLinkDisplayModeIterator* modeIter = nullptr;
    if (output->GetDisplayModeIterator(&modeIter) != S_OK || !modeIter) return nullptr;

    IDeckLinkDisplayMode* mode = nullptr;
    IDeckLinkDisplayMode* result = nullptr;

    while (modeIter->Next(&mode) == S_OK) {
        if (mode->GetWidth() == width && mode->GetHeight() == height) {
            result = mode;
            break;
        }
        mode->Release();
    }
    modeIter->Release();
    return result;
}


// ── Helper: create a DeckLink video frame from pixel data ───────────

static IDeckLinkMutableVideoFrame* createFrame(IDeckLinkOutput* output, const jint* pixels, int width, int height) {
    IDeckLinkMutableVideoFrame* frame = nullptr;
    HRESULT hr = output->CreateVideoFrame(
        width, height,
        width * 4,            // bytes per row (BGRA = 4 bytes/pixel)
        bmdFormat8BitBGRA,
        bmdFrameFlagDefault,
        &frame
    );
    if (hr != S_OK || !frame) return nullptr;

    // SDK 15.3+: GetBytes moved to IDeckLinkVideoBuffer interface
    IDeckLinkVideoBuffer* buffer = nullptr;
    frame->QueryInterface(IID_IDeckLinkVideoBuffer, reinterpret_cast<void**>(&buffer));
    if (!buffer) { frame->Release(); return nullptr; }

    void* frameBytes = nullptr;
    buffer->GetBytes(&frameBytes);
    buffer->Release();
    if (frameBytes && pixels) {
        // Java IntArray is ARGB, DeckLink expects BGRA
        // Convert: ARGB (0xAARRGGBB) → BGRA (0xBBGGRRAA)
        const uint32_t* src = reinterpret_cast<const uint32_t*>(pixels);
        uint8_t* dst = reinterpret_cast<uint8_t*>(frameBytes);
        int totalPixels = width * height;
        for (int i = 0; i < totalPixels; i++) {
            uint32_t argb = src[i];
            uint8_t a = (argb >> 24) & 0xFF;
            uint8_t r = (argb >> 16) & 0xFF;
            uint8_t g = (argb >> 8) & 0xFF;
            uint8_t b = argb & 0xFF;
            dst[i * 4 + 0] = b;
            dst[i * 4 + 1] = g;
            dst[i * 4 + 2] = r;
            dst[i * 4 + 3] = a;
        }
    }
    return frame;
}


// ═══════════════════════════════════════════════════════════════════
// JNI implementations
// ═══════════════════════════════════════════════════════════════════

JNIEXPORT jobjectArray JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeListDevices(
    JNIEnv* env, jclass)
{
#ifdef _WIN32
    ensureCOM();
#endif

    std::vector<std::string> names;

    IDeckLinkIterator* iterator = nullptr;

#ifdef _WIN32
    HRESULT hr = CoCreateInstance(
        CLSID_CDeckLinkIterator, nullptr, CLSCTX_ALL,
        IID_IDeckLinkIterator, reinterpret_cast<void**>(&iterator));
    if (FAILED(hr) || !iterator) {
        // No DeckLink drivers installed
        jclass stringClass = env->FindClass("java/lang/String");
        return env->NewObjectArray(0, stringClass, nullptr);
    }
#else
    iterator = CreateDeckLinkIteratorInstance();
    if (!iterator) {
        jclass stringClass = env->FindClass("java/lang/String");
        return env->NewObjectArray(0, stringClass, nullptr);
    }
#endif

    IDeckLink* device = nullptr;
    while (iterator->Next(&device) == S_OK) {
#ifdef _WIN32
        BSTR name = nullptr;
        if (device->GetDisplayName(&name) == S_OK && name) {
            names.push_back(bstrToString(name));
            SysFreeString(name);
        }
#elif defined(__APPLE__)
        CFStringRef name = nullptr;
        if (device->GetDisplayName(&name) == S_OK && name) {
            names.push_back(cfStringToString(name));
            CFRelease(name);
        }
#else
        const char* name = nullptr;
        if (device->GetDisplayName(&name) == S_OK && name) {
            names.push_back(name);
            free(const_cast<char*>(name));
        }
#endif
        device->Release();
    }
    iterator->Release();

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    for (size_t i = 0; i < names.size(); i++) {
        jstring jstr = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), jstr);
        env->DeleteLocalRef(jstr);
    }
    return result;
}


JNIEXPORT jboolean JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeOpen(
    JNIEnv* env, jclass, jint deviceIndex, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_mutex);

#ifdef _WIN32
    ensureCOM();
#endif

    // Close any existing output
    if (g_output) {
        g_output->Release();
        g_output = nullptr;
    }
    if (g_device) {
        g_device->Release();
        g_device = nullptr;
    }

    IDeckLinkIterator* iterator = nullptr;

#ifdef _WIN32
    HRESULT hr = CoCreateInstance(
        CLSID_CDeckLinkIterator, nullptr, CLSCTX_ALL,
        IID_IDeckLinkIterator, reinterpret_cast<void**>(&iterator));
    if (FAILED(hr) || !iterator) return JNI_FALSE;
#else
    iterator = CreateDeckLinkIteratorInstance();
    if (!iterator) return JNI_FALSE;
#endif

    IDeckLink* device = nullptr;
    int idx = 0;
    while (iterator->Next(&device) == S_OK) {
        if (idx == deviceIndex) break;
        device->Release();
        device = nullptr;
        idx++;
    }
    iterator->Release();

    if (!device) return JNI_FALSE;

    IDeckLinkOutput* output = nullptr;
    if (device->QueryInterface(IID_IDeckLinkOutput, reinterpret_cast<void**>(&output)) != S_OK || !output) {
        device->Release();
        return JNI_FALSE;
    }

    // Find a matching display mode
    IDeckLinkDisplayMode* displayMode = findDisplayMode(output, width, height);
    if (!displayMode) {
        output->Release();
        device->Release();
        return JNI_FALSE;
    }

    BMDDisplayMode modeId = displayMode->GetDisplayMode();
    displayMode->GetFrameRate(&g_frameDuration, &g_timeScale);
    displayMode->Release();

    // Enable video output
    if (output->EnableVideoOutput(modeId, bmdVideoOutputFlagDefault) != S_OK) {
        output->Release();
        device->Release();
        return JNI_FALSE;
    }

    g_output = output;
    g_device = device;
    g_playbackStarted = false;
    g_frameCount = 0;

    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeSendFrame(
    JNIEnv* env, jclass, jintArray pixels, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_output) return;

    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
    if (!pixelData) return;

    IDeckLinkMutableVideoFrame* frame = createFrame(g_output, pixelData, width, height);
    env->ReleaseIntArrayElements(pixels, pixelData, JNI_ABORT);

    if (frame) {
        g_output->DisplayVideoFrameSync(frame);
        frame->Release();
    }
}


JNIEXPORT jboolean JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeStartScheduledPlayback(
    JNIEnv* env, jclass, jint deviceIndex, jdouble fps)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_output) return JNI_FALSE;

    g_fps = fps;
    g_frameCount = 0;

    // Start scheduled playback from time 0
    if (g_output->StartScheduledPlayback(0, g_timeScale, 1.0) != S_OK) {
        return JNI_FALSE;
    }

    g_playbackStarted = true;
    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeScheduleFrame(
    JNIEnv* env, jclass, jintArray pixels, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_output || !g_playbackStarted) return;

    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
    if (!pixelData) return;

    IDeckLinkMutableVideoFrame* frame = createFrame(g_output, pixelData, width, height);
    env->ReleaseIntArrayElements(pixels, pixelData, JNI_ABORT);

    if (frame) {
        BMDTimeValue displayTime = g_frameCount * g_frameDuration;
        g_output->ScheduleVideoFrame(frame, displayTime, g_frameDuration, g_timeScale);
        g_frameCount++;
        // Note: frame is NOT released here — DeckLink SDK holds a reference
        // until the frame has been displayed. The SDK will release it.
    }
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeStopPlayback(
    JNIEnv* env, jclass)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_output) return;

    if (g_playbackStarted) {
        g_output->StopScheduledPlayback(0, nullptr, 0);
        g_playbackStarted = false;
    }
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeClose(
    JNIEnv* env, jclass)
{
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_output) {
        if (g_playbackStarted) {
            g_output->StopScheduledPlayback(0, nullptr, 0);
            g_playbackStarted = false;
        }
        g_output->DisableVideoOutput();
        g_output->Release();
        g_output = nullptr;
    }

    if (g_device) {
        g_device->Release();
        g_device = nullptr;
    }

    g_frameCount = 0;
}
