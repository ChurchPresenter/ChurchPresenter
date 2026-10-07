# Render benchmark

Off-screen render (the NDI, OMT and Browser Source path), CPU raster, density 1.
Mac OS X 26.5.1, aarch64, 10 CPUs, OpenJDK 64-Bit Server VM 21.0.10.
30+ warm-up frames, 120 measured. Times in ms.

| Scenario | Size | Render p50 | Render p99 | Readback p99 | Total p99 | Budget | |
|---|---|---:|---:|---:|---:|---:|---|
| song verse | 1920x1080 | 0.78 | 0.97 | 1.40 | 2.37 | 16.70 |  |
| song bilingual | 1920x1080 | 1.00 | 3.49 | 1.76 | 5.25 | 16.70 |  |
| song chord chart | 1920x1080 | 0.51 | 0.65 | 1.41 | 2.06 | 16.70 |  |
| bible verse | 1920x1080 | 0.40 | 0.50 | 1.39 | 1.89 | 16.70 |  |
| bible two translations | 1920x1080 | 0.42 | 0.54 | 1.36 | 1.89 | 16.70 |  |
| announcement scrolling | 1920x1080 | 0.15 | 0.19 | 1.40 | 1.59 | 16.70 |  |
| question | 1920x1080 | 0.40 | 3.46 | 1.50 | 4.96 | 16.70 |  |
| dictionary entry | 1920x1080 | 0.65 | 0.68 | 1.33 | 2.02 | 16.70 |  |
| captions | 1920x1080 | 0.23 | 0.42 | 1.38 | 1.80 | 16.70 |  |
| picture | 1920x1080 | 0.94 | 1.02 | 1.38 | 2.40 | 16.70 |  |
| canvas scene | 1920x1080 | 0.37 | 0.44 | 1.38 | 1.82 | 16.70 |  |
| lottie lower third | 1920x1080 | 0.15 | 0.17 | 1.36 | 1.53 | 16.70 |  |
| song + lower third + announcement | 1920x1080 | 1.31 | 1.83 | 1.45 | 3.28 | 16.70 |  |
| song verse | 3840x2160 | 1.79 | 2.29 | 5.65 | 7.94 | 33.30 |  |
| song bilingual | 3840x2160 | 2.09 | 3.51 | 6.01 | 9.52 | 33.30 |  |
| song chord chart | 3840x2160 | 1.77 | 1.98 | 5.63 | 7.62 | 33.30 |  |
| bible verse | 3840x2160 | 1.63 | 2.16 | 5.79 | 7.95 | 33.30 |  |
| bible two translations | 3840x2160 | 1.68 | 1.99 | 5.60 | 7.59 | 33.30 |  |
| announcement scrolling | 3840x2160 | 0.71 | 0.80 | 5.50 | 6.30 | 33.30 |  |
| question | 3840x2160 | 0.92 | 1.15 | 5.59 | 6.73 | 33.30 |  |
| dictionary entry | 3840x2160 | 1.74 | 2.03 | 5.78 | 7.81 | 33.30 |  |
| captions | 3840x2160 | 0.82 | 1.02 | 5.50 | 6.51 | 33.30 |  |
| picture | 3840x2160 | 4.15 | 4.32 | 5.65 | 9.97 | 33.30 |  |
| canvas scene | 3840x2160 | 2.06 | 2.28 | 5.63 | 7.91 | 33.30 |  |
| lottie lower third | 3840x2160 | 0.76 | 0.97 | 5.82 | 6.79 | 33.30 |  |
| song + lower third + announcement | 3840x2160 | 26.78 | 27.09 | 5.61 | 32.69 | 33.30 |  |
