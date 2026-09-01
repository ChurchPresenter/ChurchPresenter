# ffmpeg, as bundled with ChurchPresenter

ChurchPresenter opens every camera and capture card by running **ffmpeg**, and ships a copy of it
inside the application so that nothing has to be installed separately. This file is the disclosure
that shipping it requires.

## What is included

One `ffmpeg` program per platform, taken unmodified from a published build. Which build, and the
SHA-256 it is verified against, is recorded in [`gradle/ffmpeg-builds.properties`](gradle/ffmpeg-builds.properties)
— that file is the authoritative list, because it is what the build itself reads.

Nothing else from those archives is included: not `ffprobe`, not `ffplay`, and no libraries.

## Licence

The included builds are configured with `--enable-gpl`, and are therefore covered by the
**GNU General Public License, version 3 or later**. ChurchPresenter is itself released under the
GNU GPL v3 (see [`LICENSE.txt`](LICENSE.txt)), so the combined work is distributable under that same
licence — which is precisely why ffmpeg can be bundled where the NDI Runtime (whose licence forbids
redistribution) and VLC (a separate application) cannot.

ffmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project.

## Written offer of source

The complete corresponding source for the ffmpeg included in this application is the FFmpeg source
at the version named in `gradle/ffmpeg-builds.properties`, available from <https://ffmpeg.org/download.html>
and <https://github.com/FFmpeg/FFmpeg>. Each pinned URL in that file points at the publisher of the
build, whose own site documents the configuration it was built with.

If you would rather use your own ffmpeg than the one included, **Settings → Projection → Camera
Capture** takes a path to any ffmpeg on the machine and uses that instead.
