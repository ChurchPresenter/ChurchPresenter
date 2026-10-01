# Choosing the graphics backend

Which GPU API the app renders with, how to override it on a machine whose driver fares badly, and
what the override does to screen capture of a presenter output.

The app pins Metal on macOS and OpenGL on Linux, and leaves Windows to skiko, which picks Direct3D.
The choice is made on the machine running the app, not the one that built it. If a machine's GPU
driver fares badly on it — a window that freezes while presenting is the symptom to look for — name
a different one:

```shell
CHURCHPRESENTER_RENDER_API=OPENGL ./ChurchPresenter        # macOS/Linux
```
```shell
set CHURCHPRESENTER_RENDER_API=OPENGL && ChurchPresenter.exe   # Windows
```

The JVM system property `-Dchurchpresenter.renderApi=OPENGL` does the same. Accepted values are
skiko's own: `DIRECT3D`, `OPENGL`, `METAL` and `SOFTWARE`. `SOFTWARE` is a last resort — it renders
on the CPU, which a dual-output presenter app will feel.

On Windows, `OPENGL` can stop a **full-screen presenter output being visible to GDI screen capture**
— but whether it does is a property of the graphics driver, not of OpenGL, so check it on the
machine you actually present from. The output renders perfectly either way; it is only the captured
copy that is affected, and the live preview in the main window keeps showing the real thing.

Measured on one machine, two cards, same build and same workload:

| GPU | full-screen presenter under `OPENGL`, captured with `BitBlt` |
|---|---|
| NVIDIA GeForce GTX 1660 Ti | mean brightness **0.000** — solid black |
| AMD Radeon RX 6500 XT | mean brightness **0.502** — captured normally |

Under the Direct3D default both cards capture fine, and on the NVIDIA card the *main* window
captured normally at the same moment the presenter came back black. So if something on the machine
screen-captures the presenter monitor, the default is the safe choice and `OPENGL` is worth
verifying before a service.

OBS's Display Capture, measured later on the same NVIDIA GTX 1660 Ti (driver 31.0.15.5186, Windows
11), with brightness read from OBS's own frames:

| render API | DXGI Desktop Duplication | Windows Graphics Capture | GDI / `BitBlt` |
|---|---|---|---|
| default (Direct3D) | captured | captured | captured |
| `OPENGL`, first session | black | black | black |
| `OPENGL`, four later sessions | captured | captured | captured |

So on that card the default captures by every method. Under `OPENGL` the black capture came and went:
one session was black by every method until windows were moved around, and four fresh launches
afterwards captured normally, so the earlier all-black `BitBlt` result above came back once and then
not again. Treat `OPENGL` as unverified for captured outputs until checked on the machine itself.
