# Render benchmark

Off-screen render (the NDI, OMT and Browser Source path), CPU raster, density 1.
Linux 6.17.0-1022-azure, amd64, 4 CPUs, OpenJDK 64-Bit Server VM 21.0.12.1.
30+ warm-up frames, 120 measured. Times in ms.

| Scenario | Size | Render p50 | Render p99 | Readback p99 | Total p99 | Budget | |
|---|---|---:|---:|---:|---:|---:|---|
| song verse | 1920x1080 | 7.04 | 9.47 | 9.63 | 19.09 | 16.70 | over |
| song bilingual | 1920x1080 | 6.34 | 8.69 | 9.67 | 18.37 | 16.70 | over |
| song chord chart | 1920x1080 | 5.47 | 7.24 | 9.40 | 16.64 | 16.70 |  |
| bible verse | 1920x1080 | 3.96 | 4.72 | 8.64 | 13.36 | 16.70 |  |
| bible two translations | 1920x1080 | 5.24 | 6.82 | 9.82 | 16.63 | 16.70 |  |
| announcement scrolling | 1920x1080 | 1.50 | 1.98 | 7.74 | 9.73 | 16.70 |  |
| question | 1920x1080 | 3.14 | 3.82 | 9.64 | 13.46 | 16.70 |  |
| dictionary entry | 1920x1080 | 5.15 | 5.41 | 6.65 | 12.05 | 16.70 |  |
| captions | 1920x1080 | 2.85 | 4.37 | 9.36 | 13.73 | 16.70 |  |
| picture | 1920x1080 | 2.35 | 3.18 | 8.96 | 12.14 | 16.70 |  |
| canvas scene | 1920x1080 | 3.17 | 4.12 | 7.22 | 11.35 | 16.70 |  |
| lottie lower third | 1920x1080 | 1.30 | 1.67 | 9.23 | 10.90 | 16.70 |  |
| song + lower third + announcement | 1920x1080 | 8.07 | 9.65 | 9.66 | 19.31 | 16.70 | over |
| song verse | 3840x2160 | 14.36 | 17.11 | 31.63 | 48.73 | 33.30 | over |
| song bilingual | 3840x2160 | 14.76 | 17.30 | 31.57 | 48.87 | 33.30 | over |
| song chord chart | 3840x2160 | 14.61 | 15.04 | 24.27 | 39.31 | 33.30 | over |
| bible verse | 3840x2160 | 12.94 | 14.52 | 27.33 | 41.85 | 33.30 | over |
| bible two translations | 3840x2160 | 17.13 | 17.66 | 26.52 | 44.18 | 33.30 | over |
| announcement scrolling | 3840x2160 | 4.41 | 4.71 | 26.77 | 31.48 | 33.30 |  |
| question | 3840x2160 | 6.02 | 16.42 | 32.81 | 49.24 | 33.30 | over |
| dictionary entry | 3840x2160 | 12.24 | 12.83 | 26.45 | 39.28 | 33.30 | over |
| captions | 3840x2160 | 5.98 | 6.74 | 29.46 | 36.19 | 33.30 | over |
| picture | 3840x2160 | 14.06 | 14.47 | 26.95 | 41.42 | 33.30 | over |
| canvas scene | 3840x2160 | 14.92 | 15.59 | 26.80 | 42.39 | 33.30 | over |
| lottie lower third | 3840x2160 | 5.32 | 5.83 | 27.19 | 33.02 | 33.30 |  |
| song + lower third + announcement | 3840x2160 | 22.74 | 26.02 | 27.18 | 53.20 | 33.30 | over |
