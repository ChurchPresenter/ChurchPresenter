# GPU output benchmark

On-screen output windows (the projector path), drawn by Skia on the GPU (METAL).
Mac OS X 26.5.1, aarch64, 10 CPUs, OpenJDK 64-Bit Server VM 21.0.10.
60 warm-up frames, 240 measured. Frame-to-frame intervals in ms;
a frame more than 1.5x the refresh interval after the last is counted dropped.

| Scenario | Size | Refresh | Interval p50 | Interval p99 | Worst | Dropped | |
|---|---|---:|---:|---:|---:|---:|---|
| song verse | 1920x1080 | 60 Hz | 16.67 | 18.59 | 18.80 | 0 of 240 |  |
| song bilingual | 1920x1080 | 60 Hz | 16.67 | 18.63 | 18.68 | 0 of 240 |  |
| song chord chart | 1920x1080 | 60 Hz | 16.66 | 18.72 | 19.29 | 0 of 240 |  |
| bible verse | 1920x1080 | 60 Hz | 16.66 | 18.72 | 20.24 | 0 of 240 |  |
| bible two translations | 1920x1080 | 60 Hz | 16.66 | 18.71 | 18.71 | 0 of 240 |  |
| announcement scrolling | 1920x1080 | 60 Hz | 16.67 | 18.76 | 39.20 | 1 of 240 | drops |
| question | 1920x1080 | 60 Hz | 16.66 | 18.72 | 18.74 | 0 of 240 |  |
| dictionary entry | 1920x1080 | 60 Hz | 16.67 | 18.69 | 18.77 | 0 of 240 |  |
| captions | 1920x1080 | 60 Hz | 16.67 | 18.72 | 18.81 | 0 of 240 |  |
| picture | 1920x1080 | 60 Hz | 16.66 | 18.70 | 18.72 | 0 of 240 |  |
| canvas scene | 1920x1080 | 60 Hz | 16.67 | 18.70 | 18.80 | 0 of 240 |  |
| lottie lower third | 1920x1080 | 60 Hz | 16.66 | 18.73 | 40.50 | 1 of 240 | drops |
| song + lower third + announcement | 1920x1080 | 60 Hz | 16.66 | 18.69 | 18.79 | 0 of 240 |  |
