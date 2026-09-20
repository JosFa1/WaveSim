# WaveSim

WaveSim is a deliberately small Java Vulkan learning project. It opens a
resizable GLFW window, clears it with Vulkan, and draws a text FPS counter in
the upper-left corner. The window title stays `WaveSim`; the FPS is not put in
the title bar.

There is no wave simulation, geometry, or extra UI yet. The next drawing
experiment belongs in `recordCommandBuffer()` in
`src/main/java/wavesim/WaveSim.java`.

## Requirements

- Java 17 or newer
- Gradle 9 or newer
- A Vulkan loader and working Vulkan driver
- `glslc` on `PATH` for compiling the two small shaders

On CachyOS/Arch, the system dependencies are usually available with:

```bash
sudo pacman -S --needed jdk-openjdk gradle glfw vulkan-headers vulkan-loader shaderc
```

LWJGL, GLFW bindings, and Vulkan bindings are downloaded from Maven Central by
Gradle. Linux native bindings are selected in `build.gradle`.

## Build and run

```bash
gradle build
gradle run
```

The build compiles `shaders/text.vert` and `shaders/text.frag` into SPIR-V and
places them on the Java runtime classpath. The text overlay is rasterized by
Java's built-in AWT font renderer, uploaded to a Vulkan image, and rendered as
a blended textured quad. The texture is refreshed twice per second with the
current FPS.

## Project layout

- `src/main/java/wavesim/WaveSim.java` contains the Vulkan instance, device,
  swapchain, synchronization, text texture, and render loop.
- `shaders/text.vert` and `shaders/text.frag` are the only shaders needed for
  the FPS overlay.
- `recordCommandBuffer()` is the intended place to add future pipelines and
  draw calls.
