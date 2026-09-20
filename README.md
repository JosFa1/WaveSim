# WaveSim skeleton

A deliberately bare C++/Vulkan starting point. It opens a resizable, empty window, clears it with Vulkan every frame, and updates the window title with the current FPS.

## Build and run

On CachyOS/Arch, install the development packages if they are missing:

```bash
sudo pacman -S --needed cmake ninja glfw vulkan-headers
```

Then build it:

```bash
cmake -S . -B build -G Ninja
cmake --build build
./build/wave_sim
```

## Windows

This project uses GLFW and Vulkan only, so the same source builds on Windows.

1. Install Visual Studio 2022 with **Desktop development with C++**, plus CMake and Ninja.
2. Install [vcpkg](https://github.com/microsoft/vcpkg) and set the `VCPKG_ROOT` environment variable to its folder.
3. Install the dependencies and build from PowerShell:

```powershell
vcpkg install
cmake -S . -B build/windows -G Ninja -DCMAKE_BUILD_TYPE=Debug -DCMAKE_TOOLCHAIN_FILE="$env:VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"
cmake --build build/windows
.\build\windows\wave_sim.exe
```

The window is intentionally empty. Its title changes twice per second with the current FPS; this leaves the entire Vulkan render surface available for your first drawing experiment.

The compiled shader location is passed to the program at build time, so it also works when launched from an IDE with a different working directory.

## Where to start learning

- [src/main.cpp](src/main.cpp) contains the entire skeleton.
- `createSwapchain()` and `recreateSwapchain()` are the resizable-window pieces.
- `recordCommandBuffer()` is the place where you will eventually add drawing commands.
- `updateFps()` is intentionally separate and uses only GLFW's title bar, so there is no font or UI system yet.
