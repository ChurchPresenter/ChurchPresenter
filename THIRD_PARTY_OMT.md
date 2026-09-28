# Open Media Transport, as bundled with ChurchPresenter

ChurchPresenter sends its outputs as, and receives Canvas sources from, **Open Media Transport
(OMT)** sources, and ships the OMT libraries inside the application so that nothing has to be
installed separately. This file is the disclosure that shipping them requires.

## What is included

Two shared libraries per platform, placed side by side in the application's `omt` resources folder:

- **libomt** — the OMT protocol, as a C library (built from `libomt` and `libomtnet`)
- **libvmx** — the VMX video codec OMT encodes and decodes with

For Windows and macOS they are taken unmodified from the publisher's binary release. The publisher
ships none for Linux, so the Linux pair is built from their source by
[`.github/workflows/omt-linux.yml`](.github/workflows/omt-linux.yml), unmodified except in how
`libvmx` is compiled — its shared code is built without AVX2 instructions, so that it runs on the
older processors `libvmx` itself documents support for. Which release, which commits and the SHA-256
each archive is verified against are recorded in
[`gradle/omt-builds.properties`](gradle/omt-builds.properties) — that file is the authoritative
list, because it is what the build itself reads.

## Licence

Both libraries are released under the **MIT License**, Copyright (c) 2025 Open Media Transport
Contributors. The full notice is shipped beside the libraries as `LICENSE.txt`, and reads:

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
> associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or
> substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
> NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
> DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
> OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

MIT is compatible with ChurchPresenter's own GNU GPL v3, which is why these libraries can be bundled
where the NDI Runtime, whose licence forbids redistribution, cannot.

## Source

<https://github.com/openmediatransport> — `libomt`, `libomtnet` and `libvmx`.

If you would rather use a different build than the one included, **Settings → Projection → OMT
Outputs** takes a folder holding `libomt` and `libvmx` and uses those instead.
