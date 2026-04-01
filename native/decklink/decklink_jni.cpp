/**
 * DeckLink JNI native library for ChurchPresenter.
 *
 * This is a compiled C++ bridge (JNI) between the Java/Kotlin application and
 * the BlackMagic DeckLink SDK. It's written in C++ because the DeckLink SDK is
 * a native C++/COM library — there is no Java API for DeckLink hardware.
 * This bridge exposes device enumeration, video output configuration,
 * frame delivery, and video input capture to the Kotlin side via JNI
 * (Java Native Interface).
 *
 * The compiled output (decklink_jni.dll / .dylib / .so) is bundled with the app.
 * End users do NOT need the SDK — only the BlackMagic Desktop Video drivers.
 *
 * Supports multiple simultaneous device outputs (e.g. SDI + HDMI on separate cards)
 * and video input capture from DeckLink devices.
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

#include <atomic>
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


// ── Per-device input state ──────────────────────────────────────────

struct InputState {
    IDeckLinkInput* input = nullptr;
    IDeckLink* device = nullptr;
    uint32_t* frameData = nullptr;
    int frameWidth = 0;
    int frameHeight = 0;
    bool hasNewFrame = false;
    std::mutex frameMutex;
};

static std::mutex g_inputMutex;
static std::map<int, InputState*> g_inputs;


// ── Input callback ──────────────────────────────────────────────────

class DeckLinkInputCallback : public IDeckLinkInputCallback {
public:
    DeckLinkInputCallback(int deviceIndex) : m_deviceIndex(deviceIndex), m_refCount(1) {}

    // IUnknown
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID, LPVOID*) override { return E_NOINTERFACE; }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refCount; }
    ULONG STDMETHODCALLTYPE Release() override {
        ULONG ref = --m_refCount;
        if (ref == 0) delete this;
        return ref;
    }

    HRESULT STDMETHODCALLTYPE VideoInputFormatChanged(
        BMDVideoInputFormatChangedEvents events,
        IDeckLinkDisplayMode* newMode,
        BMDDetectedVideoInputFormatFlags flags) override
    {
        // Auto-mode: restart input with the detected format
        std::lock_guard<std::mutex> lock(g_inputMutex);
        auto it = g_inputs.find(m_deviceIndex);
        if (it == g_inputs.end() || !it->second->input) return S_OK;

        int w = static_cast<int>(newMode->GetWidth());
        int h = static_cast<int>(newMode->GetHeight());

        // Determine pixel format based on detection flags
        BMDPixelFormat pixFmt = bmdFormat8BitYUV;
        if (flags & bmdDetectedVideoInputRGB444) {
            pixFmt = bmdFormat8BitBGRA;
        }

        it->second->input->PauseStreams();
        it->second->input->EnableVideoInput(
            newMode->GetDisplayMode(), pixFmt,
            bmdVideoInputEnableFormatDetection);
        it->second->input->FlushStreams();
        it->second->input->StartStreams();

        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE VideoInputFrameArrived(
        IDeckLinkVideoInputFrame* videoFrame,
        IDeckLinkAudioInputPacket*) override
    {
        if (!videoFrame || (videoFrame->GetFlags() & bmdFrameHasNoInputSource)) return S_OK;

        int w = static_cast<int>(videoFrame->GetWidth());
        int h = static_cast<int>(videoFrame->GetHeight());
        if (w <= 0 || h <= 0) return S_OK;

        // Get raw frame bytes via IDeckLinkVideoBuffer (SDK 15.3+)
        void* frameBytes = nullptr;
        IDeckLinkVideoBuffer* videoBuffer = nullptr;
        if (videoFrame->QueryInterface(IID_IDeckLinkVideoBuffer, reinterpret_cast<void**>(&videoBuffer)) != S_OK || !videoBuffer) return S_OK;
        videoBuffer->StartAccess(bmdBufferAccessRead);
        if (videoBuffer->GetBytes(&frameBytes) != S_OK || !frameBytes) {
            videoBuffer->EndAccess(bmdBufferAccessRead);
            videoBuffer->Release();
            return S_OK;
        }

        int totalPixels = w * h;
        BMDPixelFormat pixFmt = videoFrame->GetPixelFormat();

        // Allocate/resize buffer if needed
        std::lock_guard<std::mutex> lock(g_inputMutex);
        auto it = g_inputs.find(m_deviceIndex);
        if (it == g_inputs.end()) {
            videoBuffer->EndAccess(bmdBufferAccessRead);
            videoBuffer->Release();
            return S_OK;
        }
        InputState* state = it->second;

        if (state->frameWidth != w || state->frameHeight != h) {
            delete[] state->frameData;
            state->frameData = new uint32_t[totalPixels];
            state->frameWidth = w;
            state->frameHeight = h;
        }

        if (pixFmt == bmdFormat8BitBGRA) {
            // BGRA → ARGB: swap byte order
            const uint8_t* src = reinterpret_cast<const uint8_t*>(frameBytes);
            for (int i = 0; i < totalPixels; i++) {
                uint8_t b = src[i * 4 + 0];
                uint8_t g = src[i * 4 + 1];
                uint8_t r = src[i * 4 + 2];
                uint8_t a = src[i * 4 + 3];
                state->frameData[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        } else if (pixFmt == bmdFormat8BitYUV) {
            // UYVY (packed YUV 4:2:2) → ARGB
            const uint8_t* src = reinterpret_cast<const uint8_t*>(frameBytes);
            for (int i = 0; i < totalPixels; i += 2) {
                int j = i * 2; // 4 bytes per 2 pixels
                uint8_t u  = src[j + 0];
                uint8_t y0 = src[j + 1];
                uint8_t v  = src[j + 2];
                uint8_t y1 = src[j + 3];

                auto clamp = [](int val) -> uint8_t {
                    return static_cast<uint8_t>(val < 0 ? 0 : (val > 255 ? 255 : val));
                };
                int c0 = y0 - 16, c1 = y1 - 16;
                int d = u - 128, e = v - 128;

                state->frameData[i] = (0xFF << 24) |
                    (clamp((298 * c0 + 409 * e + 128) >> 8) << 16) |
                    (clamp((298 * c0 - 100 * d - 208 * e + 128) >> 8) << 8) |
                    clamp((298 * c0 + 516 * d + 128) >> 8);

                if (i + 1 < totalPixels) {
                    state->frameData[i + 1] = (0xFF << 24) |
                        (clamp((298 * c1 + 409 * e + 128) >> 8) << 16) |
                        (clamp((298 * c1 - 100 * d - 208 * e + 128) >> 8) << 8) |
                        clamp((298 * c1 + 516 * d + 128) >> 8);
                }
            }
        } else if (pixFmt == bmdFormat10BitYUV) {
            // v210 format — treat as black (unsupported for now)
            memset(state->frameData, 0, totalPixels * 4);
        } else {
            // Unknown format — try raw copy as BGRA
            const uint8_t* src = reinterpret_cast<const uint8_t*>(frameBytes);
            for (int i = 0; i < totalPixels; i++) {
                uint8_t b = src[i * 4 + 0];
                uint8_t g = src[i * 4 + 1];
                uint8_t r = src[i * 4 + 2];
                state->frameData[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        state->hasNewFrame = true;
        videoBuffer->EndAccess(bmdBufferAccessRead);
        videoBuffer->Release();
        return S_OK;
    }

private:
    int m_deviceIndex;
    std::atomic<ULONG> m_refCount;
};


// ── Helper: get device by index ──────────────────────────────────────

static IDeckLink* getDeviceByIndex(int deviceIndex) {
    IDeckLinkIterator* iterator = nullptr;
#ifdef _WIN32
    ensureCOM();
    HRESULT hr = CoCreateInstance(
        CLSID_CDeckLinkIterator, nullptr, CLSCTX_ALL,
        IID_IDeckLinkIterator, reinterpret_cast<void**>(&iterator));
    if (FAILED(hr) || !iterator) return nullptr;
#else
    iterator = CreateDeckLinkIteratorInstance();
    if (!iterator) return nullptr;
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
    return device;
}


// ── Helper: get device display name (platform-specific) ──────────────

static std::string getDeviceName(IDeckLink* device) {
#ifdef _WIN32
    BSTR name = nullptr;
    if (device->GetDisplayName(&name) == S_OK && name) {
        std::string result = bstrToString(name);
        SysFreeString(name);
        return result;
    }
#elif defined(__APPLE__)
    CFStringRef name = nullptr;
    if (device->GetDisplayName(&name) == S_OK && name) {
        std::string result = cfStringToString(name);
        CFRelease(name);
        return result;
    }
#else
    const char* name = nullptr;
    if (device->GetDisplayName(&name) == S_OK && name) {
        std::string result(name);
        free(const_cast<char*>(name));
        return result;
    }
#endif
    return "";
}

// ── Helper: get display mode name (platform-specific) ────────────────

static std::string getModeName(IDeckLinkDisplayMode* mode) {
#ifdef _WIN32
    BSTR name = nullptr;
    if (mode->GetName(&name) == S_OK && name) {
        std::string result = bstrToString(name);
        SysFreeString(name);
        return result;
    }
#elif defined(__APPLE__)
    CFStringRef name = nullptr;
    if (mode->GetName(&name) == S_OK && name) {
        std::string result = cfStringToString(name);
        CFRelease(name);
        return result;
    }
#else
    const char* name = nullptr;
    if (mode->GetName(&name) == S_OK && name) {
        std::string result(name);
        free(const_cast<char*>(name));
        return result;
    }
#endif
    return "";
}


// ── Helper: get the device's default output mode from DeckLink Setup ──

static IDeckLinkDisplayMode* getConfiguredDisplayMode(IDeckLink* device, IDeckLinkOutput* output) {
    IDeckLinkConfiguration* config = nullptr;
    if (device->QueryInterface(IID_IDeckLinkConfiguration, reinterpret_cast<void**>(&config)) == S_OK && config) {
        int64_t modeValue = 0;
        // bmdDeckLinkConfigDefaultVideoOutputMode = 'dvom' = 0x64766F6D
        if (config->GetInt(static_cast<BMDDeckLinkConfigurationID>(0x64766F6D), &modeValue) == S_OK && modeValue != 0) {
            BMDDisplayMode configuredModeId = static_cast<BMDDisplayMode>(modeValue);
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

    IDeckLinkDisplayMode* displayMode = getConfiguredDisplayMode(device, output);
    if (displayMode) {
    } else {
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


// ═══════════════════════════════════════════════════════════════════
// Input capture JNI implementations
// ═══════════════════════════════════════════════════════════════════

// Helper to build a JNI string array
static jobjectArray toStringArray(JNIEnv* env, const std::vector<std::string>& vec) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(vec.size()), stringClass, nullptr);
    for (size_t i = 0; i < vec.size(); i++) {
        jstring jstr = env->NewStringUTF(vec[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), jstr);
        env->DeleteLocalRef(jstr);
    }
    return result;
}


JNIEXPORT jobjectArray JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeListInputModes(
    JNIEnv* env, jclass, jint deviceIndex)
{
#ifdef _WIN32
    ensureCOM();
#endif

    std::vector<std::string> modes;
    IDeckLink* device = getDeviceByIndex(deviceIndex);
    if (!device) return toStringArray(env, modes);

    IDeckLinkInput* input = nullptr;
    if (device->QueryInterface(IID_IDeckLinkInput, reinterpret_cast<void**>(&input)) == S_OK && input) {
        IDeckLinkDisplayModeIterator* modeIter = nullptr;
        if (input->GetDisplayModeIterator(&modeIter) == S_OK && modeIter) {
            IDeckLinkDisplayMode* mode = nullptr;
            while (modeIter->Next(&mode) == S_OK) {
                std::string name = getModeName(mode);
                int w = static_cast<int>(mode->GetWidth());
                int h = static_cast<int>(mode->GetHeight());
                BMDTimeValue dur; BMDTimeScale scale;
                mode->GetFrameRate(&dur, &scale);
                double fps = (dur > 0) ? static_cast<double>(scale) / static_cast<double>(dur) : 0;
                BMDFieldDominance fd = mode->GetFieldDominance();
                const char* scan = (fd == bmdProgressiveFrame || fd == bmdProgressiveSegmentedFrame) ? "p" : "i";

                // Format: "name|WxH@fps_scan" e.g. "HD 1080p 30|1920x1080@30p"
                char encoded[256];
                snprintf(encoded, sizeof(encoded), "%s|%dx%d@%.2f%s",
                    name.c_str(), w, h, fps, scan);
                modes.push_back(encoded);
                mode->Release();
            }
            modeIter->Release();
        }
        input->Release();
    }
    device->Release();

    return toStringArray(env, modes);
}


JNIEXPORT jobjectArray JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeListVideoConnections(
    JNIEnv* env, jclass, jint deviceIndex)
{
#ifdef _WIN32
    ensureCOM();
#endif

    std::vector<std::string> connections;
    IDeckLink* device = getDeviceByIndex(deviceIndex);
    if (!device) return toStringArray(env, connections);

    IDeckLinkProfileAttributes* attrs = nullptr;
    if (device->QueryInterface(IID_IDeckLinkProfileAttributes, reinterpret_cast<void**>(&attrs)) == S_OK && attrs) {
        int64_t availableConnections = 0;
        // bmdDeckLinkVideoInputConnections = 'vicn' = 0x7669636E
        if (attrs->GetInt(static_cast<BMDDeckLinkAttributeID>(0x7669636E), &availableConnections) == S_OK) {
            // Encode as "name|value" pairs
            if (availableConnections & bmdVideoConnectionSDI)
                connections.push_back("SDI|1");
            if (availableConnections & bmdVideoConnectionHDMI)
                connections.push_back("HDMI|2");
            if (availableConnections & bmdVideoConnectionOpticalSDI)
                connections.push_back("Optical SDI|4");
            if (availableConnections & bmdVideoConnectionComponent)
                connections.push_back("Component|8");
            if (availableConnections & bmdVideoConnectionComposite)
                connections.push_back("Composite|16");
            if (availableConnections & bmdVideoConnectionSVideo)
                connections.push_back("S-Video|32");
        }
        attrs->Release();
    }
    device->Release();

    return toStringArray(env, connections);
}


JNIEXPORT jboolean JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeOpenInput(
    JNIEnv* env, jclass, jint deviceIndex, jstring modeStr, jint connectionType)
{
#ifdef _WIN32
    ensureCOM();
#endif

    // Close existing input for this device
    {
        std::lock_guard<std::mutex> lock(g_inputMutex);
        auto it = g_inputs.find(deviceIndex);
        if (it != g_inputs.end()) {
            InputState* old = it->second;
            if (old->input) {
                old->input->StopStreams();
                old->input->DisableVideoInput();
                old->input->Release();
            }
            if (old->device) old->device->Release();
            delete[] old->frameData;
            delete old;
            g_inputs.erase(it);
        }
    }

    IDeckLink* device = getDeviceByIndex(deviceIndex);
    if (!device) return JNI_FALSE;

    // Set video connection BEFORE getting input interface
    if (connectionType > 0) {
        IDeckLinkConfiguration* config = nullptr;
        if (device->QueryInterface(IID_IDeckLinkConfiguration, reinterpret_cast<void**>(&config)) == S_OK && config) {
            // bmdDeckLinkConfigVideoInputConnection = 'vicn' = 0x7669636E
            HRESULT hr = config->SetInt(static_cast<BMDDeckLinkConfigurationID>(0x7669636E),
                           static_cast<int64_t>(connectionType));
            config->Release();
        }
    }

    IDeckLinkInput* input = nullptr;
    if (device->QueryInterface(IID_IDeckLinkInput, reinterpret_cast<void**>(&input)) != S_OK || !input) {
        device->Release();
        fprintf(stderr, "[DeckLink Input] Device %d: no input interface\n", deviceIndex);
        return JNI_FALSE;
    }

    // Parse mode string to find display mode, or use auto-detect
    const char* modeChars = modeStr ? env->GetStringUTFChars(modeStr, nullptr) : nullptr;
    std::string modeString = modeChars ? modeChars : "";
    if (modeChars) env->ReleaseStringUTFChars(modeStr, modeChars);

    BMDDisplayMode selectedMode = bmdModeNTSC; // fallback
    BMDVideoInputFlags inputFlags = bmdVideoInputEnableFormatDetection;
    bool autoMode = modeString.empty();

    if (!autoMode) {
        // Parse "WxH@fps" from the encoded mode string (after '|')
        size_t pipePos = modeString.find('|');
        std::string encoded = (pipePos != std::string::npos) ? modeString.substr(pipePos + 1) : modeString;
        int tw = 0, th = 0;
        double tfps = 0;
        if (sscanf(encoded.c_str(), "%dx%d@%lf", &tw, &th, &tfps) >= 2) {
            // Find matching mode
            IDeckLinkDisplayModeIterator* modeIter = nullptr;
            if (input->GetDisplayModeIterator(&modeIter) == S_OK && modeIter) {
                IDeckLinkDisplayMode* mode = nullptr;
                while (modeIter->Next(&mode) == S_OK) {
                    if (mode->GetWidth() == tw && mode->GetHeight() == th) {
                        BMDTimeValue dur; BMDTimeScale scale;
                        mode->GetFrameRate(&dur, &scale);
                        double fps = (dur > 0) ? static_cast<double>(scale) / static_cast<double>(dur) : 0;
                        if (tfps <= 0 || (fps > tfps - 1.0 && fps < tfps + 1.0)) {
                            selectedMode = mode->GetDisplayMode();
                            mode->Release();
                            break;
                        }
                    }
                    mode->Release();
                }
                modeIter->Release();
            }
        }
    } else {
        // Auto mode: pick the first available mode as a starting point,
        // format detection will switch to the actual input signal
        IDeckLinkDisplayModeIterator* modeIter = nullptr;
        if (input->GetDisplayModeIterator(&modeIter) == S_OK && modeIter) {
            IDeckLinkDisplayMode* mode = nullptr;
            if (modeIter->Next(&mode) == S_OK) {
                selectedMode = mode->GetDisplayMode();
                mode->Release();
            }
            modeIter->Release();
        }
    }

    // Set up callback
    InputState* state = new InputState();
    state->input = input;
    state->device = device;

    DeckLinkInputCallback* callback = new DeckLinkInputCallback(deviceIndex);
    input->SetCallback(callback);
    callback->Release(); // input holds a reference

    // Store state before enabling (callback may fire immediately)
    {
        std::lock_guard<std::mutex> lock(g_inputMutex);
        g_inputs[deviceIndex] = state;
    }

    HRESULT hr = input->EnableVideoInput(selectedMode, bmdFormat8BitYUV, inputFlags);
    if (hr != S_OK) {
        fprintf(stderr, "[DeckLink Input] Device %d: EnableVideoInput failed: 0x%08lx\n",
                deviceIndex, (unsigned long)hr);
        std::lock_guard<std::mutex> lock(g_inputMutex);
        g_inputs.erase(deviceIndex);
        input->SetCallback(nullptr);
        input->Release();
        device->Release();
        delete state;
        return JNI_FALSE;
    }

    hr = input->StartStreams();
    if (hr != S_OK) {
        fprintf(stderr, "[DeckLink Input] Device %d: StartStreams failed: 0x%08lx\n",
                deviceIndex, (unsigned long)hr);
        input->DisableVideoInput();
        std::lock_guard<std::mutex> lock(g_inputMutex);
        g_inputs.erase(deviceIndex);
        input->SetCallback(nullptr);
        input->Release();
        device->Release();
        delete state;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}


JNIEXPORT jintArray JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeGetInputFrame(
    JNIEnv* env, jclass, jint deviceIndex)
{
    std::lock_guard<std::mutex> lock(g_inputMutex);
    auto it = g_inputs.find(deviceIndex);
    if (it == g_inputs.end()) return nullptr;

    InputState* state = it->second;
    std::lock_guard<std::mutex> frameLock(state->frameMutex);

    if (!state->hasNewFrame || !state->frameData || state->frameWidth <= 0 || state->frameHeight <= 0)
        return nullptr;

    state->hasNewFrame = false;
    int totalPixels = state->frameWidth * state->frameHeight;

    // First 2 elements are width and height, followed by pixel data
    jintArray result = env->NewIntArray(2 + totalPixels);
    if (!result) return nullptr;

    jint header[2] = { state->frameWidth, state->frameHeight };
    env->SetIntArrayRegion(result, 0, 2, header);
    env->SetIntArrayRegion(result, 2, totalPixels, reinterpret_cast<jint*>(state->frameData));

    return result;
}


JNIEXPORT void JNICALL
Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_nativeCloseInput(
    JNIEnv* env, jclass, jint deviceIndex)
{
    std::lock_guard<std::mutex> lock(g_inputMutex);
    auto it = g_inputs.find(deviceIndex);
    if (it == g_inputs.end()) return;

    InputState* state = it->second;
    if (state->input) {
        state->input->StopStreams();
        state->input->SetCallback(nullptr);
        state->input->DisableVideoInput();
        state->input->Release();
    }
    if (state->device) state->device->Release();
    delete[] state->frameData;
    delete state;
    g_inputs.erase(it);

}
