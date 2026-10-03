# Render benchmark

Off-screen render (the NDI, OMT and Browser Source path), CPU raster, density 1.
Linux 6.18.44-fc-v64, amd64, 4 CPUs, OpenJDK 64-Bit Server VM 21.0.11.
30+ warm-up frames, 120 measured. Times in ms.

| Scenario | Size | Render p50 | Render p99 | Readback p99 | Total p99 | Budget | |
|---|---|---:|---:|---:|---:|---:|---|
| song verse | 1920x1080 | 11.58 | 34.67 | 11.53 | 46.19 | 16.70 | over |
| song bilingual | 1920x1080 | 12.33 | 22.35 | 12.28 | 34.62 | 16.70 | over |
| song chord chart | 1920x1080 | 5.28 | 7.79 | 10.24 | 18.02 | 16.70 | over |
| bible verse | 1920x1080 | 4.17 | 6.69 | 10.51 | 17.20 | 16.70 | over |
| bible two translations | 1920x1080 | 4.90 | 6.41 | 8.87 | 15.29 | 16.70 |  |
| announcement scrolling | 1920x1080 | 1.29 | 2.13 | 11.96 | 14.09 | 16.70 |  |
| question | 1920x1080 | 2.85 | 4.28 | 9.85 | 14.14 | 16.70 |  |
| dictionary entry | 1920x1080 | 5.56 | 7.47 | 10.91 | 18.38 | 16.70 | over |
| captions | 1920x1080 | 2.87 | 4.44 | 7.58 | 12.02 | 16.70 |  |
| picture | 1920x1080 | 2.15 | 3.59 | 9.63 | 13.22 | 16.70 |  |
| canvas scene | 1920x1080 | 2.99 | 6.15 | 11.83 | 17.98 | 16.70 | over |
| lottie lower third | 1920x1080 | 1.07 | 1.43 | 7.15 | 8.57 | 16.70 |  |
| song verse | 3840x2160 | 16.58 | 23.43 | 45.50 | 68.93 | 33.30 | over |
| song bilingual | 3840x2160 | 17.40 | 23.67 | 51.20 | 74.87 | 33.30 | over |
| song chord chart | 3840x2160 | 17.33 | 20.97 | 45.81 | 66.78 | 33.30 | over |
| bible verse | 3840x2160 | 15.07 | 24.99 | 43.97 | 68.96 | 33.30 | over |
| bible two translations | 3840x2160 | 19.95 | 28.65 | 43.89 | 72.53 | 33.30 | over |
| announcement scrolling | 3840x2160 | 5.87 | 8.16 | 41.11 | 49.26 | 33.30 | over |
| question | 3840x2160 | 7.57 | 10.22 | 46.48 | 56.70 | 33.30 | over |
| dictionary entry | 3840x2160 | 14.05 | 17.70 | 38.47 | 56.17 | 33.30 | over |
| captions | 3840x2160 | 7.74 | 9.34 | 51.10 | 60.44 | 33.30 | over |
| picture | 3840x2160 | 16.02 | 20.53 | 41.28 | 61.80 | 33.30 | over |
| canvas scene | 3840x2160 | 14.51 | 20.62 | 44.24 | 64.85 | 33.30 | over |
| lottie lower third | 3840x2160 | 6.51 | 7.94 | 39.21 | 47.15 | 33.30 | over |
