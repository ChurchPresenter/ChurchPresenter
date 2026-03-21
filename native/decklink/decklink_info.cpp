#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <comdef.h>
#include <cstdio>
#include <string>
#include "DeckLinkAPI_h.h"

static std::string bstrToString(BSTR bstr) {
    if (!bstr) return "";
    int len = SysStringLen(bstr);
    int size = WideCharToMultiByte(CP_UTF8, 0, bstr, len, nullptr, 0, nullptr, nullptr);
    std::string result(size, 0);
    WideCharToMultiByte(CP_UTF8, 0, bstr, len, &result[0], size, nullptr, nullptr);
    return result;
}

int main() {
    CoInitializeEx(nullptr, COINIT_MULTITHREADED);

    IDeckLinkIterator* iterator = nullptr;
    HRESULT hr = CoCreateInstance(CLSID_CDeckLinkIterator, nullptr, CLSCTX_ALL,
        IID_IDeckLinkIterator, reinterpret_cast<void**>(&iterator));
    if (FAILED(hr) || !iterator) {
        printf("No DeckLink drivers installed or no devices found.\n");
        return 1;
    }

    IDeckLink* device = nullptr;
    int idx = 0;
    while (iterator->Next(&device) == S_OK) {
        BSTR name = nullptr;
        device->GetDisplayName(&name);
        std::string nameStr = name ? bstrToString(name) : "Unknown";
        printf("\n=== Device %d: %s ===\n", idx, nameStr.c_str());
        if (name) SysFreeString(name);

        // Check for output support
        IDeckLinkOutput* output = nullptr;
        if (device->QueryInterface(IID_IDeckLinkOutput, reinterpret_cast<void**>(&output)) == S_OK && output) {
            printf("  Has video output: YES\n");

            // Get configured default mode
            IDeckLinkConfiguration* config = nullptr;
            if (device->QueryInterface(IID_IDeckLinkConfiguration, reinterpret_cast<void**>(&config)) == S_OK && config) {
                int64_t modeValue = 0;
                if (config->GetInt(static_cast<BMDDeckLinkConfigurationID>(0x64766F6D), &modeValue) == S_OK && modeValue != 0) {
                    printf("  Default output mode ID: 0x%08llx\n", (unsigned long long)modeValue);
                }
                int64_t connType = 0;
                if (config->GetInt(static_cast<BMDDeckLinkConfigurationID>(0x766F636E), &connType) == S_OK) {
                    printf("  Output connection: 0x%08llx", (unsigned long long)connType);
                    if (connType & 0x01) printf(" [SDI]");
                    if (connType & 0x02) printf(" [HDMI]");
                    if (connType & 0x04) printf(" [OpticalSDI]");
                    if (connType & 0x08) printf(" [Component]");
                    if (connType & 0x10) printf(" [Composite]");
                    if (connType & 0x20) printf(" [SVideo]");
                    printf("\n");
                }
                config->Release();
            }

            // List output display modes
            IDeckLinkDisplayModeIterator* modeIter = nullptr;
            if (output->GetDisplayModeIterator(&modeIter) == S_OK && modeIter) {
                printf("  Output modes:\n");
                IDeckLinkDisplayMode* mode = nullptr;
                while (modeIter->Next(&mode) == S_OK) {
                    BMDTimeValue dur; BMDTimeScale scale;
                    mode->GetFrameRate(&dur, &scale);
                    double fps = (dur > 0) ? (double)scale / (double)dur : 0;
                    BMDFieldDominance fd = mode->GetFieldDominance();
                    const char* scan = (fd == bmdProgressiveFrame || fd == bmdProgressiveSegmentedFrame) ? "p" : "i";

                    BSTR modeName = nullptr;
                    mode->GetName(&modeName);
                    std::string modeNameStr = modeName ? bstrToString(modeName) : "?";
                    printf("    %s - %ldx%ld%s @ %.2f fps (mode=0x%08lx)\n",
                        modeNameStr.c_str(),
                        (long)mode->GetWidth(), (long)mode->GetHeight(), scan, fps,
                        (unsigned long)mode->GetDisplayMode());
                    if (modeName) SysFreeString(modeName);
                    mode->Release();
                }
                modeIter->Release();
            }
            output->Release();
        } else {
            printf("  Has video output: NO\n");
        }

        // Check for input support
        IDeckLinkInput* input = nullptr;
        if (device->QueryInterface(IID_IDeckLinkInput, reinterpret_cast<void**>(&input)) == S_OK && input) {
            printf("  Has video input: YES\n");
            input->Release();
        } else {
            printf("  Has video input: NO\n");
        }

        // Check profile attributes
        IDeckLinkProfileAttributes* attrs = nullptr;
        if (device->QueryInterface(IID_IDeckLinkProfileAttributes, reinterpret_cast<void**>(&attrs)) == S_OK && attrs) {
            int64_t subDeviceIndex = -1;
            // bmdDeckLinkSubDeviceIndex = 'subi' = 0x73756269
            if (attrs->GetInt(static_cast<BMDDeckLinkAttributeID>(0x73756269), &subDeviceIndex) == S_OK) {
                printf("  Sub-device index: %lld\n", subDeviceIndex);
            }
            int64_t numSubDevices = 0;
            // bmdDeckLinkNumberOfSubDevices = 'nsub' = 0x6E737562
            if (attrs->GetInt(static_cast<BMDDeckLinkAttributeID>(0x6E737562), &numSubDevices) == S_OK) {
                printf("  Number of sub-devices: %lld\n", numSubDevices);
            }
            int64_t persistent = -1;
            // bmdDeckLinkPersistentID = 'peid' = 0x70656964
            if (attrs->GetInt(static_cast<BMDDeckLinkAttributeID>(0x70656964), &persistent) == S_OK) {
                printf("  Persistent ID: %lld\n", persistent);
            }
            int64_t duplex = 0;
            // bmdDeckLinkDuplex = 'dupx' = 0x64757078
            if (attrs->GetInt(static_cast<BMDDeckLinkAttributeID>(0x64757078), &duplex) == S_OK) {
                printf("  Duplex mode: %lld", duplex);
                if (duplex == 0) printf(" [inactive]");
                else if (duplex == /* 'fdup' */ 0x66647570) printf(" [full]");
                else if (duplex == /* 'hdup' */ 0x68647570) printf(" [half]");
                printf("\n");
            }
            attrs->Release();
        }

        device->Release();
        idx++;
    }
    iterator->Release();

    if (idx == 0) {
        printf("No DeckLink devices found.\n");
    } else {
        printf("\nTotal devices: %d\n", idx);
    }

    CoUninitialize();
    return 0;
}
