# WaveSim

WaveSim is a deliberately small Java Vulkan learning project. It opens a
resizable GLFW window, clears it with Vulkan, and draws a text FPS counter in
the upper-left corner. The window title stays `WaveSim`; the FPS is not put in
the title bar.

There is no wave simulation, geometry, or extra UI yet. The next drawing
experiment belongs in `recordCommandBuffer()` in
`src/main/java/wavesim/VulkanRenderer.java`.

## Requirements

- Java 17 or newer
- Gradle 9 or newer
- A Vulkan loader and working Vulkan driver
- `glslc` on `PATH` for compiling the two small shaders

On CachyOS/Arch, install the build dependencies with:

```bash
sudo pacman -S --needed jdk-openjdk gradle shaderc vulkan-icd-loader vulkan-tools
```

Install the Vulkan driver for the GPU in the machine as well if it is not
already present. For example, Intel systems use `vulkan-intel`, AMD systems
use `vulkan-radeon`, and NVIDIA systems normally use `nvidia-utils`.

The Java, GLFW, and Vulkan LWJGL bindings are downloaded from Maven Central by
Gradle. The Linux native bindings are selected in `build.gradle`; a system
`glfw` package and Vulkan development headers are not required for this Java
build.

You can check the prerequisites before building:

```bash
java --version
gradle --version
glslc --version
vulkaninfo --summary
```

## Build and run

```bash
gradle clean build
gradle run
```

`gradle clean build` compiles the Java source, compiles
`shaders/text.vert` and `shaders/text.frag` into SPIR-V, and creates the JAR
and application distributions under `build/`. `gradle run` opens the Vulkan
window and keeps running until the window is closed.

If `glslc` is installed outside `PATH`, provide its path through `GLSLC`:

```bash
GLSLC=/path/to/glslc gradle clean build
```

The text overlay is rasterized by Java's built-in AWT font renderer, uploaded
to a Vulkan image, and rendered as a blended textured quad. The texture is
refreshed twice per second with the current FPS.

## Project layout

- `src/main/java/wavesim/WaveSim.java` connects the focused classes and owns
  the main application loop.
- `src/main/java/wavesim/WindowManager.java` creates the GLFW window and
  reports resize events.
- `src/main/java/wavesim/FpsCounter.java` measures frames per second without
  knowing anything about Vulkan.
- `src/main/java/wavesim/VulkanRenderer.java` owns the Vulkan instance,
  device, swapchain, synchronization, text texture, and drawing commands.
- `shaders/text.vert` and `shaders/text.frag` are the only shaders needed for
  the FPS overlay.
- `recordCommandBuffer()` in `VulkanRenderer.java` is the intended place to
  add future pipelines and draw calls.

The classes are split by responsibility on purpose. For example, you can
change the FPS calculation in `FpsCounter.java` without needing to
understand Vulkan synchronization first.
