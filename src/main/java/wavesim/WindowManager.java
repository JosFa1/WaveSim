package wavesim;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVulkan;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Owns the GLFW window and the small amount of window state that the
 * renderer needs to know about.
 *
 * GLFW is the library that creates the operating-system window. Vulkan draws
 * into that window, but it does not create the window itself.
 */
public final class WindowManager implements AutoCloseable {
    private static final int WINDOW_WIDTH = 1100;
    private static final int WINDOW_HEIGHT = 650;

    private long window;
    private boolean glfwInitialized;
    private boolean framebufferResized;

    /**
     * Starts GLFW and creates a window that Vulkan can use.
     */
    public void initialize() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Could not initialize GLFW");
        }
        glfwInitialized = true;

        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new IllegalStateException("GLFW could not find a Vulkan loader");
        }

        GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = GLFW.glfwCreateWindow(
                WINDOW_WIDTH,
                WINDOW_HEIGHT,
                "WaveSim",
                NULL,
                NULL
        );
        if (window == NULL) {
            throw new IllegalStateException("Could not create the window");
        }

        // Vulkan swapchain images must be rebuilt after a resize.
        GLFW.glfwSetFramebufferSizeCallback(
                window,
                (resizedWindow, width, height) -> framebufferResized = true
        );
    }

    /**
     * Returns the native GLFW handle used by LWJGL and Vulkan.
     */
    public long handle() {
        return window;
    }

    /**
     * Returns true when the user has closed the window.
     */
    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(window);
    }

    /**
     * Lets GLFW process keyboard, mouse, and window events.
     */
    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    /**
     * Writes the current framebuffer width and height into the supplied
     * buffers. The renderer uses this when it creates a swapchain.
     */
    public void getFramebufferSize(IntBuffer width, IntBuffer height) {
        GLFW.glfwGetFramebufferSize(window, width, height);
    }

    /**
     * Used while the window is minimized, so the program does not busy-wait.
     */
    public void waitForEvents() {
        GLFW.glfwWaitEvents();
    }

    /**
     * Tells the renderer whether the framebuffer size changed.
     */
    public boolean wasResized() {
        return framebufferResized;
    }

    /**
     * Marks the resize event as handled after the swapchain is rebuilt.
     */
    public void clearResized() {
        framebufferResized = false;
    }

    /**
     * Destroys the window and shuts down GLFW.
     */
    @Override
    public void close() {
        if (window != NULL) {
            GLFW.glfwDestroyWindow(window);
            window = NULL;
        }

        GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        if (glfwInitialized) {
            GLFW.glfwTerminate();
            glfwInitialized = false;
        }
    }
}
