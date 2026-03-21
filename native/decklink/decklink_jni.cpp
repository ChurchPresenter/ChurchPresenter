/**
 * DeckLink JNI native library for ChurchPresenter.
 *
 * This is a compiled C++ bridge (JNI) between the Java/Kotlin application and
 * the BlackMagic DeckLink SDK. It's written in C++ because the DeckLink SDK is
 * a native C++/COM library — there is no Java API for DeckLink hardware.
 * This bridge exposes device enumeration, video output configuration, and
 * frame delivery to the Kotlin side via JNI (Java Native Interface).
 *
 * The compiled output (decklink_jni.dll / .dylib / .so) is bundled with the app.
 * End users do NOT need the SDK — only the BlackMagic Desktop Video drivers.
 *
 * Supports multiple simultaneous device outputs (e.g. SDI + HDMI on separate cards).
 *
 * Build requirements:
 *   - BlackMagic DeckLink SDK (free download from blackmagicdesign.com)
 *   - BlackMagic Desktop Video drivers installed
 *   - JDK with JNI headers
 *   - CMake 3.15+ (or Visual Studio directly)
 *
 * Windows: uses COM (CoInitializeEx / CoCreateInstance)
 * macOS:   uses CoreFoundation / DeckLinkAPI.framework
 *
 * See BUILD.md for step-by-step compilation instructions.
 */

#include "org_churchpresenter_app_churchpresenter_composables_DeckLinkManager.h"

#include <cstring>
#include <string>
#include <vector>
#include <map>
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


// ── Per-device state ─────────────────────────────────────────────────

struct DeviceState {
    IDeckLinkOutput* output = nullptr;
    IDeckLink* device = nullptr;
    bool playbackStarted = false;
    long frameCount = 0;
    double fps = 30.0;
    BMDTimeScale timeScale = 30000;
    BMDTimeValue frameDuration = 1001;
    int outputWidth = 0;
    int outputHeight = 0;
};

static std::mutex g_mutex;
static std::map<int, DeviceState> g_devices;


// ── Helper: get the device's default output mode from DeckLink Setup ──

static IDeckLinkDisplayMode* getConfiguredDisplayMode(IDeckLink* device, IDeckLinkOutput* output) {
    IDeckLinkConfiguration* config = nullptr;
    if (device->QueryInterface(IID_IDeckLinkConfiguration, reinterpret_cast<void**>(&config)) == S_OK && config) {
        int64_t modeValue = 0;
        // bmdDeckLinkConfigDefaultVideoOutputMode = 'dvom' = 0x64766F6D
        if (config->GetInt(static_cast<BMDDeckLinkConfigurationID>(0x64766F6D), &modeValue) == S_OK && modeValue != 0) {
            BMDDisplayMode configuredModeId = static_cast<BMDDisplayMode>(modeValue);
            fprintf(stderr, "[DeckLink] Config default output mode: 0x%08lx\n", (unsigned long)configuredModeId);
            IDeckLinkDisplayModeIterator* modeIter = nullptr;
            if (output->GetDisplayModeIterator(&modeIter) == S_OK && modeIter) {
                IDeckLinkDisplayMode* mode = nullptr;
                while (modeIter->Next(&mode) == S_OK) {
                    if (mode->GetDisplayMode() == configuredModeId) {
                        modeIter->Release();
                        config->Release();
                        return mode;
                    }
                    mode->Release();
                }
                modeIter->Release();
            }
        } else {
            fprintf(stderr, "[DeckLink] Config query for default output mode failed\n");
        }
        config->Release();
    }
    return nullptr;
}

// ── Helper: find display mode matching resolution ─────────────────────

static IDeckLinkDisplayMode* findDisplayMode(IDeckLinkOutput* output, int width, int height) {
    IDeckLinkDisplayModeIterator* modeIter = nullptr;
    if (output->GetDisplayModeIterator(&modeIter) != S_OK || !modeIter) return nullptr;

    IDeckLinkDisplayMode* mode = nullptr;
    IDeckLinkDisplayMode* bestMode = nullptr;
    double bestFps = 0.0;
    bool bestIs5994 = false;

    while (modeIter->Next(&mode) == S_OK) {
        if (mode->GetWidth() == width && mode->GetHeight() == height) {
            BMDFieldDominance fieldDom = mode->GetFieldDominance();
            bool isProgressive = (fieldDom == bmdProgressiveFrame || fieldDom == bmdProgressiveSegmentedFrame);
            if (!isProgressive) { mode->Release(); continue; }

            BMDTimeValue duration; BMDTimeScale scale;
            mode->GetFrameRate(&duration, &scale);
            double fps = (duration > 0) ? static_cast<double>(scale) / static_cast<double>(duration) : 0.0;
            bool is5994 = (duration == 1001 && scale == 60000);

            bool isBetter = (is5994 && !bestIs5994) || (!bestIs5994 && fps > bestFps) || (is5994 && bestIs5994 && fps > bestFps);
            if (isBetter) {
                if (bestMode) bestMode->Release();
                bestMode = mode;
                bestFps = fps;
                bestIs5994 = is5994;
            } else {
                mode->Release();
            }
        } else {
            mode->Release();
        }
    }
    modeIter->Release();
    return bestMode;
}


// ── Helper: create a DeckLink video frame from pixel data ───────────

static IDeckLinkMutableVideoFrame* createFrame(IDeckLinkOutput* output, const jint* pixels, int width, int height) {
    IDeckLinkMutableVideoFrame* frame = nullptr;
    HRESULT hr = output->CreateVideoFrame(
        width, height, width * 4,
        bmdFormat8BitBGRA, bmdFrameFlagDefault, &frame
    );
    if (hr != S_OK || !frame) return nullptr;

    void* frameBytes = nullptr;
    IDeckLinkVideoBuffer* buffer = nullptr;
    if (frame->QueryInterface(IID_IDeckLinkVideoBuffer, reinterpret_cast<void**>(&buffer)) == S_OK && buffer) {
        buffer->StartAccess(bmdBufferAccessWrite);
        buffer->GetBytes(&frameBytes);
    }

    if (!frameBytes) {
        if (buffer) { buffer->EndAccess(bmdBufferAccessWrite); buffer->Release(); }
        frame->Release();
        return nullptr;
    }

    if (pixels) {
        const uint32_t* src = reinterpret_cast<const uint32_t*>(pixels);
        uint8_t* dst = reinterpret_cast<uint8_t*>(frameBytes);
        int totalPixels = width * height;
        for (int i = 0; i < totalPixels; i++) {
            uint32_t argb = src[i];
            dst[i * 4 + 0] = argb & 0xFF;           // B
            dst[i * 4 + 1] = (argb >> 8) & 0xFF;    // G
            dst[i * 4 + 2] = (argb >> 16) & 0xFF;   // R
            dst[i * 4 + 3] = (argb >> 24) & 0xFF;   // A
        }
    }

    if (buffer) {
        buffer->EndAccess(bmdBufferAccessWrite);
        buffer->Release();
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

    // Close existing output for this device index if any
    auto it = g_devices.find(deviceIndex);
    if (it != g_devices.end()) {
        if (it->second.output) {
            it->second.output->DisableVideoOutput();
            it->second.output->Release();
        }
        if (it->second.device) it->second.device->Release();
        g_devices.erase(it);
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

    fprintf(stderr, "[DeckLink] Opening device %d, requested %dx%d\n", deviceIndex, width, height);
    IDeckLinkDisplayMode* displayMode = getConfiguredDisplayMode(device, output);
    if (displayMode) {
        fprintf(stderr, "[DeckLink] Using default mode from DeckLink Setup\n");
    } else {
        fprintf(stderr, "[DeckLink] Config query failed, falling back to resolution match\n");
        displayMode = findDisplayMode(output, width, height);
    }
    if (!displayMode) {
        output->Release(); device->Release();
        return JNI_FALSE;
    }

    DeviceState state;
    BMDDisplayMode modeId = displayMode->GetDisplayMode();
    displayMode->GetFrameRate(&state.frameDuration, &state.timeScale);
    state.outputWidth = static_cast<int>(displayMode->GetWidth());
    state.outputHeight = static_cast<int>(displayMode->GetHeight());
    state.fps = (state.timeScale > 0 && state.frameDuration > 0)
        ? static_cast<double>(state.timeScale) / static_cast<double>(state.frameDuration) : 30.0;
    displayMode->Release();

    fprintf(stderr, "[DeckLink] Device %d: %dx%d @ %.2f fps\n",
            deviceIndex, state.outputWidth, state.outputHeight, state.fps);

    HRESULT enableResult = output->EnableVideoOutput(modeId, bmdVideoOutputFlagDefault);
    if (enableResult != S_OK) {
        fprintf(stderr, "[DeckLink] Device %d: EnableVideoOutput failed: 0x%08lx\n",
                deviceIndex, (unsigned long)enableResult);
        output->Release(); device->Release();
        return JNI_FALSE;
    }

    state.output = output;
    state.device = device;
    g_devices[deviceIndex] = state;

    fprintf(stderr, "[DeckLink] Device %d opened successfully\n", deviceIndex);
    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeSendFrame(
    JNIEnv* env, jclass, jint deviceIndex, jintArray pixels, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(deviceIndex);
    if (it == g_devices.end() || !it->second.output) return;

    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
    if (!pixelData) return;

    IDeckLinkMutableVideoFrame* frame = createFrame(it->second.output, pixelData, width, height);
    env->ReleaseIntArrayElements(pixels, pixelData, JNI_ABORT);

    if (frame) {
        it->second.output->DisplayVideoFrameSync(frame);
        frame->Release();
    }
}


JNIEXPORT jboolean JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeStartScheduledPlayback(
    JNIEnv* env, jclass, jint deviceIndex, jdouble fps)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(deviceIndex);
    if (it == g_devices.end() || !it->second.output) return JNI_FALSE;

    it->second.fps = fps;
    it->second.frameCount = 0;

    if (it->second.output->StartScheduledPlayback(0, it->second.timeScale, 1.0) != S_OK) {
        return JNI_FALSE;
    }

    it->second.playbackStarted = true;
    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeScheduleFrame(
    JNIEnv* env, jclass, jint deviceIndex, jintArray pixels, jint width, jint height)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(deviceIndex);
    if (it == g_devices.end() || !it->second.output || !it->second.playbackStarted) return;

    jint* pixelData = env->GetIntArrayElements(pixels, nullptr);
    if (!pixelData) return;

    IDeckLinkMutableVideoFrame* frame = createFrame(it->second.output, pixelData, width, height);
    env->ReleaseIntArrayElements(pixels, pixelData, JNI_ABORT);

    if (frame) {
        BMDTimeValue displayTime = it->second.frameCount * it->second.frameDuration;
        it->second.output->ScheduleVideoFrame(frame, displayTime, it->second.frameDuration, it->second.timeScale);
        it->second.frameCount++;
    }
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeStopPlayback(
    JNIEnv* env, jclass, jint deviceIndex)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(deviceIndex);
    if (it == g_devices.end() || !it->second.output) return;

    if (it->second.playbackStarted) {
        it->second.output->StopScheduledPlayback(0, nullptr, 0);
        it->second.playbackStarted = false;
    }
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeClose(
    JNIEnv* env, jclass, jint deviceIndex)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_devices.find(deviceIndex);
    if (it == g_devices.end()) return;

    if (it->second.output) {
        if (it->second.playbackStarted) {
            it->second.output->StopScheduledPlayback(0, nullptr, 0);
        }
        it->second.output->DisableVideoOutput();
        it->second.output->Release();
    }
    if (it->second.device) {
        it->second.device->Release();
    }
    g_devices.erase(it);

    fprintf(stderr, "[DeckLink] Device %d closed\n", deviceIndex);
}


JNIEXPORT jintArray JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeGetOutputInfo(
    JNIEnv* env, jclass, jint deviceIndex)
{
    std::lock_guard<std::mutex> lock(g_mutex);

    jint info[4] = {0, 0, 0, 0};
    auto it = g_devices.find(deviceIndex);
    if (it != g_devices.end()) {
        info[0] = it->second.outputWidth;
        info[1] = it->second.outputHeight;
        info[2] = static_cast<jint>(it->second.timeScale);
        info[3] = static_cast<jint>(it->second.frameDuration);
    }

    jintArray result = env->NewIntArray(4);
    if (result) {
        env->SetIntArrayRegion(result, 0, 4, info);
    }
    return result;
}
