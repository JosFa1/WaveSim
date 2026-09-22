package wavesim;

/**
 * The small application coordinator.
 *
 * WaveSim does not contain Vulkan setup details. It only connects the focused
 * classes and describes the order in which the program runs:
 *
 * 1. Create the GLFW window. 2. Create the Vulkan renderer. 3. Repeat the frame
 * loop until the user closes the window. 4. Clean up everything in reverse
 * order.
 */
public final class WaveSim {

    public static void main(String[] args) {
        // Vulkan device extension lists can exceed LWJGL's default native
        // stack size on systems with multiple GPUs and driver extensions.
        System.setProperty("org.lwjgl.system.stackSize", "1048576");
        new WaveSim().run();
    }

    private void run() {
        WindowManager window = new WindowManager();
        VulkanRenderer renderer = new VulkanRenderer(window);
        WaveGenerator wave = new WaveGenerator(100);

        FpsCounter fpsCounter = new FpsCounter();

        try {
            window.initialize();
            renderer.initialize();

            // This is the main game/rendering loop.
            while (!window.shouldClose()) {
                window.pollEvents();

                // Most frames do not need a text update. The counter returns
                // null until half a second of frames has been measured.
                String newFpsText = fpsCounter.recordFrame();
                if (newFpsText != null) {
                    renderer.updateFpsText(newFpsText);
                }

                // Build the complete image before submitting the frame. The
                // renderer composes the wave and FPS text into one texture.
                renderer.drawWave(wave);
                renderer.drawFrame();
            }

            renderer.waitForIdle();
        } catch (RuntimeException error) {
            System.err.println("WaveSim error: " + error.getMessage());
            throw error;
        } finally {
            // Cleanup is placed in finally so it also runs when initialization
            // or rendering reports an error.
            renderer.cleanup();
            window.close();
        }
    }
}
