# Render benchmark

Off-screen render (the NDI, OMT and Browser Source path), CPU raster, density 1.
Mac OS X 26.5.1, aarch64, 10 CPUs, OpenJDK 64-Bit Server VM 21.0.10.
30+ warm-up frames, 120 measured. Times in ms.

| Scenario | Size | Render p50 | Render p99 | Readback p99 | Total p99 | Budget | |
|---|---|---:|---:|---:|---:|---:|---|
| song verse | 1920x1080 | 0.91 | 1.53 | 1.46 | 2.99 | 16.70 |  |
| song bilingual | 1920x1080 | 0.83 | 1.29 | 1.45 | 2.74 | 16.70 |  |
| song chord chart | 1920x1080 | 0.52 | 0.69 | 1.40 | 2.09 | 16.70 |  |
| bible verse | 1920x1080 | 0.38 | 0.51 | 1.39 | 1.91 | 16.70 |  |
| bible two translations | 1920x1080 | 0.41 | 0.48 | 1.39 | 1.87 | 16.70 |  |
| announcement scrolling | 1920x1080 | 0.15 | 0.18 | 1.37 | 1.55 | 16.70 |  |
| question | 1920x1080 | 0.32 | 0.51 | 1.40 | 1.91 | 16.70 |  |
| dictionary entry | 1920x1080 | 0.69 | 0.74 | 1.37 | 2.11 | 16.70 |  |
| captions | 1920x1080 | 0.25 | 0.41 | 1.37 | 1.78 | 16.70 |  |
| picture | 1920x1080 | 0.96 | 1.02 | 1.37 | 2.39 | 16.70 |  |
| canvas scene | 1920x1080 | 0.37 | 0.41 | 1.38 | 1.79 | 16.70 |  |
| lottie lower third | 1920x1080 | 0.16 | 0.20 | 1.37 | 1.57 | 16.70 |  |
| song + lower third + announcement | 1920x1080 | 1.35 | 1.68 | 1.41 | 3.08 | 16.70 |  |
| song verse | 3840x2160 | 1.83 | 2.20 | 5.74 | 7.93 | 33.30 |  |
| song bilingual | 3840x2160 | 1.96 | 2.37 | 5.67 | 8.03 | 33.30 |  |
| song chord chart | 3840x2160 | 1.76 | 2.02 | 5.64 | 7.66 | 33.30 |  |
| bible verse | 3840x2160 | 1.59 | 2.41 | 6.54 | 8.95 | 33.30 |  |
| bible two translations | 3840x2160 | 1.72 | 1.89 | 5.65 | 7.54 | 33.30 |  |
| announcement scrolling | 3840x2160 | 0.72 | 0.83 | 5.62 | 6.45 | 33.30 |  |
| question | 3840x2160 | 0.99 | 1.27 | 5.68 | 6.94 | 33.30 |  |
| dictionary entry | 3840x2160 | 1.80 | 1.92 | 5.59 | 7.51 | 33.30 |  |
| captions | 3840x2160 | 0.84 | 1.11 | 5.70 | 6.81 | 33.30 |  |
| picture | 3840x2160 | 4.20 | 4.48 | 5.71 | 10.18 | 33.30 |  |
| canvas scene | 3840x2160 | 2.11 | 2.37 | 5.69 | 8.07 | 33.30 |  |
| lottie lower third | 3840x2160 | 0.77 | 0.87 | 5.62 | 6.49 | 33.30 |  |
| song + lower third + announcement | 3840x2160 | 5.40 | 5.68 | 5.71 | 11.38 | 33.30 |  |
