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
- `glslc` on `PATH`, in `VULKAN_SDK`, or configured through `GLSLC`

## Windows setup

Open PowerShell and install Java and the Vulkan SDK with WinGet:

```powershell
winget install --id Microsoft.OpenJDK.21 --exact
winget install --id KhronosGroup.VulkanSDK --exact
```

Gradle is easiest to install with Scoop. If Scoop is not installed yet, run
these commands first:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression
scoop install gradle
```

Close and reopen PowerShell after installation so the new commands and Vulkan
environment variables are available. From the project directory, verify the
toolchain and build:

```powershell
java --version
gradle --version
vulkaninfo --summary
gradle clean build
```

The Vulkan SDK normally sets `VULKAN_SDK` automatically. If the compiler is
installed elsewhere, provide its full path for the current PowerShell session:

```powershell
$env:GLSLC = 'C:\path\to\glslc.exe'
gradle clean build
```

On CachyOS/Arch, install the build dependencies with:

```bash
sudo pacman -S --needed jdk-openjdk gradle shaderc vulkan-icd-loader vulkan-tools
```

Install the Vulkan driver for the GPU in the machine as well if it is not
already present. For example, Intel systems use `vulkan-intel`, AMD systems
use `vulkan-radeon`, and NVIDIA systems normally use `nvidia-utils`.

The Java, GLFW, and Vulkan LWJGL bindings are downloaded from Maven Central by
Gradle. The matching LWJGL native bindings are selected automatically for
Windows, macOS, and Linux; a system `glfw` package and Vulkan development
headers are not required for this Java build.

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

If `glslc` is installed outside `PATH`, set `VULKAN_SDK` to the SDK directory
or provide the compiler path through `GLSLC`:

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
