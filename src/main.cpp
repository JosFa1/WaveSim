// WaveSim is currently a small Vulkan + GLFW learning project.
//
// The program opens a window, clears it with Vulkan, and shows the current
// frames per second (FPS) in the title bar.  There is no actual wave
// simulation yet.  The best place to add drawing code later is
// recordCommandBuffer().

// This tells GLFW to include the Vulkan definitions it needs for creating a
// window surface.  It must be defined before including GLFW's header.
#define GLFW_INCLUDE_VULKAN
#include <GLFW/glfw3.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <optional>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

// We can keep this many frames in progress at the same time.  Two frames is
// a common starting point because it lets the CPU prepare one frame while
// the GPU is finishing the previous frame.
constexpr uint32_t kFramesInFlight = 2;

constexpr int kWindowWidth = 1100;
constexpr int kWindowHeight = 650;

// Throwing an exception gives main() one place to print Vulkan/GLFW errors.
[[noreturn]] void fail(const std::string& message) {
    throw std::runtime_error(message);
}

// Most Vulkan functions return a VkResult instead of throwing an exception.
// This small helper converts an unsuccessful result into the same error path
// used by the rest of the program.
void check(VkResult result, const char* action) {
    if (result != VK_SUCCESS) {
        fail(std::string(action) + " failed: " + std::to_string(result));
    }
}

// A physical device can use different queue families for drawing and for
// presenting an image to the window.  Keep both family numbers together.
struct QueueFamilies {
    std::optional<uint32_t> graphicsFamily;
    std::optional<uint32_t> presentFamily;

    bool complete() const {
        return graphicsFamily.has_value() && presentFamily.has_value();
    }
};

class App {
public:
    void run() {
        createWindow();
        createVulkan();
        mainLoop();
        cleanup();
    }

private:
    // ----------------------------- Window ------------------------------

    GLFWwindow* window{};

    // ------------------------- Vulkan objects --------------------------
    // Vulkan objects are handles owned by the Vulkan driver.  They are
    // created during startup and released in cleanup().
    VkInstance instance{};
    VkSurfaceKHR surface{};
    VkPhysicalDevice gpu{};
    VkDevice device{};

    VkQueue graphicsQueue{};
    VkQueue presentQueue{};

    // The swapchain is the group of images that Vulkan can show in the
    // window.  Its dependent objects are recreated when the window changes
    // size or becomes visible again after being minimized.
    VkSwapchainKHR swapchain{};
    VkFormat swapchainFormat{};
    VkExtent2D swapchainExtent{};
    std::vector<VkImage> swapchainImages;
    std::vector<VkImageView> imageViews;
    VkRenderPass renderPass{};
    std::vector<VkFramebuffer> framebuffers;

    // Command buffers contain instructions for the GPU.  A command pool is
    // the allocator used to create them.
    VkCommandPool commandPool{};
    std::array<VkCommandBuffer, kFramesInFlight> commandBuffers{};

    // Semaphores coordinate the GPU stages.  Fences let the CPU wait until a
    // frame has finished before reusing its command buffer.
    std::array<VkSemaphore, kFramesInFlight> imageAvailable{};
    std::array<VkSemaphore, kFramesInFlight> renderFinished{};
    std::array<VkFence, kFramesInFlight> inFlight{};

    uint32_t currentFrame{};
    bool resized{};

    // Values used by updateFps().
    uint32_t frameCount{};
    std::chrono::steady_clock::time_point fpsStart{};

    // -------------------------- Window setup ---------------------------

    void createWindow() {
        if (!glfwInit()) {
            fail("Could not initialize GLFW");
        }

        // Vulkan creates the graphics context, so GLFW must not create an
        // OpenGL context for this window.
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(
            kWindowWidth,
            kWindowHeight,
            "WaveSim | FPS: --",
            nullptr,
            nullptr);

        if (!window) {
            fail("Could not create the window");
        }

        // GLFW callbacks do not know which App object they belong to.  Store
        // this pointer so the resize callback can reach the App instance.
        glfwSetWindowUserPointer(window, this);
        glfwSetFramebufferSizeCallback(window, [](GLFWwindow* resizedWindow, int, int) {
            auto* app = static_cast<App*>(glfwGetWindowUserPointer(resizedWindow));
            app->resized = true;
        });

        fpsStart = std::chrono::steady_clock::now();
    }

    // -------------------------- Vulkan setup ---------------------------

    void createVulkan() {
        createInstance();

        // A surface connects Vulkan to the native GLFW window.  It must be
        // created after the Vulkan instance and before choosing a GPU.
        check(
            glfwCreateWindowSurface(instance, window, nullptr, &surface),
            "Creating window surface");

        pickGpu();
        createDevice();
        createCommandPool();
        createSwapchain();
        createCommandBuffers();
        createSyncObjects();
    }

    void createInstance() {
        VkApplicationInfo appInfo{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        appInfo.pApplicationName = "WaveSim";
        appInfo.apiVersion = VK_API_VERSION_1_1;

        // GLFW tells us which instance extensions are required by the
        // current operating system and window system.
        uint32_t extensionCount{};
        const char** requiredExtensions =
            glfwGetRequiredInstanceExtensions(&extensionCount);

        VkInstanceCreateInfo createInfo{
            VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        createInfo.pApplicationInfo = &appInfo;
        createInfo.enabledExtensionCount = extensionCount;
        createInfo.ppEnabledExtensionNames = requiredExtensions;

        check(
            vkCreateInstance(&createInfo, nullptr, &instance),
            "Creating Vulkan instance");
    }

    QueueFamilies findQueueFamilies(VkPhysicalDevice candidate) const {
        QueueFamilies queues;

        uint32_t familyCount{};
        vkGetPhysicalDeviceQueueFamilyProperties(
            candidate,
            &familyCount,
            nullptr);

        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(
            candidate,
            &familyCount,
            families.data());

        for (uint32_t familyIndex = 0; familyIndex < familyCount; ++familyIndex) {
            const VkQueueFamilyProperties& family = families[familyIndex];

            if (family.queueFlags & VK_QUEUE_GRAPHICS_BIT) {
                queues.graphicsFamily = familyIndex;
            }

            VkBool32 canPresent{};
            vkGetPhysicalDeviceSurfaceSupportKHR(
                candidate,
                familyIndex,
                surface,
                &canPresent);

            if (canPresent) {
                queues.presentFamily = familyIndex;
            }
        }

        return queues;
    }

    bool supportsSwapchain(VkPhysicalDevice candidate) const {
        uint32_t extensionCount{};
        vkEnumerateDeviceExtensionProperties(
            candidate,
            nullptr,
            &extensionCount,
            nullptr);

        std::vector<VkExtensionProperties> extensions(extensionCount);
        vkEnumerateDeviceExtensionProperties(
            candidate,
            nullptr,
            &extensionCount,
            extensions.data());

        for (const VkExtensionProperties& extension : extensions) {
            if (std::string(extension.extensionName) ==
                VK_KHR_SWAPCHAIN_EXTENSION_NAME) {
                return true;
            }
        }

        return false;
    }

    void pickGpu() {
        uint32_t deviceCount{};
        vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);

        if (deviceCount == 0) {
            fail("No Vulkan-capable GPU was found");
        }

        std::vector<VkPhysicalDevice> candidates(deviceCount);
        vkEnumeratePhysicalDevices(instance, &deviceCount, candidates.data());

        for (VkPhysicalDevice candidate : candidates) {
            const QueueFamilies queues = findQueueFamilies(candidate);

            if (queues.complete() && supportsSwapchain(candidate)) {
                gpu = candidate;
                return;
            }
        }

        fail("No GPU can draw to this window");
    }

    void createDevice() {
        const QueueFamilies queues = findQueueFamilies(gpu);

        // Usually the graphics and presentation queues are the same family,
        // but Vulkan allows them to be different.  Create one queue for each
        // distinct family that this application needs.
        std::vector<uint32_t> uniqueFamilies{*queues.graphicsFamily};
        if (*queues.presentFamily != *queues.graphicsFamily) {
            uniqueFamilies.push_back(*queues.presentFamily);
        }

        const float queuePriority = 1.0f;
        std::vector<VkDeviceQueueCreateInfo> queueCreateInfos;

        for (uint32_t familyIndex : uniqueFamilies) {
            VkDeviceQueueCreateInfo queueCreateInfo{
                VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
            queueCreateInfo.queueFamilyIndex = familyIndex;
            queueCreateInfo.queueCount = 1;
            queueCreateInfo.pQueuePriorities = &queuePriority;
            queueCreateInfos.push_back(queueCreateInfo);
        }

        const char* deviceExtensions[] = {
            VK_KHR_SWAPCHAIN_EXTENSION_NAME};

        VkDeviceCreateInfo createInfo{
            VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        createInfo.queueCreateInfoCount =
            static_cast<uint32_t>(queueCreateInfos.size());
        createInfo.pQueueCreateInfos = queueCreateInfos.data();
        createInfo.enabledExtensionCount = 1;
        createInfo.ppEnabledExtensionNames = deviceExtensions;

        check(
            vkCreateDevice(gpu, &createInfo, nullptr, &device),
            "Creating logical device");

        vkGetDeviceQueue(
            device,
            *queues.graphicsFamily,
            0,
            &graphicsQueue);
        vkGetDeviceQueue(
            device,
            *queues.presentFamily,
            0,
            &presentQueue);
    }

    // ----------------------- Swapchain setup ---------------------------

    VkSurfaceFormatKHR chooseSurfaceFormat(
        const std::vector<VkSurfaceFormatKHR>& formats) const {
        // Prefer an sRGB format because it gives the clear color a more
        // predictable appearance on a normal monitor.
        for (const VkSurfaceFormatKHR& format : formats) {
            if (format.format == VK_FORMAT_B8G8R8A8_SRGB &&
                format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }

        // The GPU guarantees that the list contains at least one format.
        return formats.front();
    }

    VkExtent2D chooseSwapchainExtent(
        const VkSurfaceCapabilitiesKHR& capabilities) const {
        // Some window systems provide the exact size for us.
        if (capabilities.currentExtent.width !=
            std::numeric_limits<uint32_t>::max()) {
            return capabilities.currentExtent;
        }

        // Otherwise, ask GLFW for the framebuffer size.  It can be different
        // from the window size on high-DPI displays, so use the framebuffer
        // dimensions rather than the original window dimensions.
        int width{};
        int height{};
        glfwGetFramebufferSize(window, &width, &height);

        VkExtent2D extent{};
        extent.width = std::clamp(
            static_cast<uint32_t>(width),
            capabilities.minImageExtent.width,
            capabilities.maxImageExtent.width);
        extent.height = std::clamp(
            static_cast<uint32_t>(height),
            capabilities.minImageExtent.height,
            capabilities.maxImageExtent.height);
        return extent;
    }

    void createSwapchain() {
        VkSurfaceCapabilitiesKHR capabilities{};
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
            gpu,
            surface,
            &capabilities);

        uint32_t formatCount{};
        vkGetPhysicalDeviceSurfaceFormatsKHR(
            gpu,
            surface,
            &formatCount,
            nullptr);

        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        vkGetPhysicalDeviceSurfaceFormatsKHR(
            gpu,
            surface,
            &formatCount,
            formats.data());

        const VkSurfaceFormatKHR surfaceFormat = chooseSurfaceFormat(formats);
        swapchainFormat = surfaceFormat.format;
        swapchainExtent = chooseSwapchainExtent(capabilities);

        // Ask for one more image than the minimum so the GPU and CPU have
        // some room to work independently.  Respect the driver's maximum.
        uint32_t imageCount = capabilities.minImageCount + 1;
        if (capabilities.maxImageCount != 0 &&
            imageCount > capabilities.maxImageCount) {
            imageCount = capabilities.maxImageCount;
        }

        const QueueFamilies queues = findQueueFamilies(gpu);
        const uint32_t queueFamilyIndices[] = {
            *queues.graphicsFamily,
            *queues.presentFamily};

        VkSwapchainCreateInfoKHR createInfo{
            VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        createInfo.surface = surface;
        createInfo.minImageCount = imageCount;
        createInfo.imageFormat = swapchainFormat;
        createInfo.imageColorSpace = surfaceFormat.colorSpace;
        createInfo.imageExtent = swapchainExtent;
        createInfo.imageArrayLayers = 1;
        createInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

        // If the queue families differ, both queues need access to the
        // swapchain images.  Otherwise, exclusive access is more efficient.
        if (*queues.graphicsFamily != *queues.presentFamily) {
            createInfo.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
            createInfo.queueFamilyIndexCount = 2;
            createInfo.pQueueFamilyIndices = queueFamilyIndices;
        } else {
            createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        }

        createInfo.preTransform = capabilities.currentTransform;
        createInfo.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        // FIFO is guaranteed to be available and is synchronized to the
        // display, which prevents tearing.
        createInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        createInfo.clipped = VK_TRUE;

        check(
            vkCreateSwapchainKHR(device, &createInfo, nullptr, &swapchain),
            "Creating swapchain");

        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr);
        swapchainImages.resize(imageCount);
        vkGetSwapchainImagesKHR(
            device,
            swapchain,
            &imageCount,
            swapchainImages.data());

        createImageViews();
        createRenderPass();
        createFramebuffers();
    }

    void createImageViews() {
        imageViews.resize(swapchainImages.size());

        for (size_t imageIndex = 0; imageIndex < swapchainImages.size();
             ++imageIndex) {
            VkImageViewCreateInfo createInfo{
                VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            createInfo.image = swapchainImages[imageIndex];
            createInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            createInfo.format = swapchainFormat;
            createInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            createInfo.subresourceRange.levelCount = 1;
            createInfo.subresourceRange.layerCount = 1;

            check(
                vkCreateImageView(
                    device,
                    &createInfo,
                    nullptr,
                    &imageViews[imageIndex]),
                "Creating image view");
        }
    }

    void createRenderPass() {
        // This render pass has one color attachment: the image that will be
        // displayed in the window.  CLEAR starts it with our background color
        // and STORE keeps the result for presentation.
        VkAttachmentDescription colorAttachment{};
        colorAttachment.format = swapchainFormat;
        colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

        VkAttachmentReference colorReference{
            0,
            VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorReference;

        VkRenderPassCreateInfo createInfo{
            VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        createInfo.attachmentCount = 1;
        createInfo.pAttachments = &colorAttachment;
        createInfo.subpassCount = 1;
        createInfo.pSubpasses = &subpass;

        check(
            vkCreateRenderPass(device, &createInfo, nullptr, &renderPass),
            "Creating render pass");
    }

    void createFramebuffers() {
        framebuffers.resize(imageViews.size());

        for (size_t imageIndex = 0; imageIndex < imageViews.size();
             ++imageIndex) {
            VkFramebufferCreateInfo createInfo{
                VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            createInfo.renderPass = renderPass;
            createInfo.attachmentCount = 1;
            createInfo.pAttachments = &imageViews[imageIndex];
            createInfo.width = swapchainExtent.width;
            createInfo.height = swapchainExtent.height;
            createInfo.layers = 1;

            check(
                vkCreateFramebuffer(
                    device,
                    &createInfo,
                    nullptr,
                    &framebuffers[imageIndex]),
                "Creating framebuffer");
        }
    }

    // --------------------- Commands and synchronization ----------------

    void createCommandPool() {
        VkCommandPoolCreateInfo createInfo{
            VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        createInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        createInfo.queueFamilyIndex = *findQueueFamilies(gpu).graphicsFamily;

        check(
            vkCreateCommandPool(device, &createInfo, nullptr, &commandPool),
            "Creating command pool");
    }

    void createCommandBuffers() {
        VkCommandBufferAllocateInfo allocateInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        allocateInfo.commandPool = commandPool;
        allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocateInfo.commandBufferCount = kFramesInFlight;

        check(
            vkAllocateCommandBuffers(
                device,
                &allocateInfo,
                commandBuffers.data()),
            "Allocating command buffers");
    }

    void createSyncObjects() {
        VkSemaphoreCreateInfo semaphoreInfo{
            VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};

        VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        // Start signaled so the first frame does not wait forever for a
        // previous frame that does not exist yet.
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

        for (uint32_t frameIndex = 0; frameIndex < kFramesInFlight;
             ++frameIndex) {
            check(
                vkCreateSemaphore(
                    device,
                    &semaphoreInfo,
                    nullptr,
                    &imageAvailable[frameIndex]),
                "Creating image semaphore");
            check(
                vkCreateSemaphore(
                    device,
                    &semaphoreInfo,
                    nullptr,
                    &renderFinished[frameIndex]),
                "Creating render semaphore");
            check(
                vkCreateFence(
                    device,
                    &fenceInfo,
                    nullptr,
                    &inFlight[frameIndex]),
                "Creating fence");
        }
    }

    // This is the main extension point for future graphics work.  To draw
    // something later, add a graphics pipeline and vkCmdDraw calls between
    // vkCmdBeginRenderPass() and vkCmdEndRenderPass().
    void recordCommandBuffer(
        VkCommandBuffer commandBuffer,
        uint32_t imageIndex) {
        VkCommandBufferBeginInfo beginInfo{
            VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        check(
            vkBeginCommandBuffer(commandBuffer, &beginInfo),
            "Beginning command buffer");

        // For now, the render pass only clears the window to a dark blue.
        VkClearValue clearColor{};
        clearColor.color = {{0.015f, 0.020f, 0.035f, 1.0f}};

        VkRenderPassBeginInfo renderPassInfo{
            VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        renderPassInfo.renderPass = renderPass;
        renderPassInfo.framebuffer = framebuffers[imageIndex];
        renderPassInfo.renderArea.extent = swapchainExtent;
        renderPassInfo.clearValueCount = 1;
        renderPassInfo.pClearValues = &clearColor;

        vkCmdBeginRenderPass(
            commandBuffer,
            &renderPassInfo,
            VK_SUBPASS_CONTENTS_INLINE);
        vkCmdEndRenderPass(commandBuffer);

        check(
            vkEndCommandBuffer(commandBuffer),
            "Ending command buffer");
    }

    // ----------------------------- Drawing ------------------------------

    void drawFrame() {
        // Do not reuse this frame's command buffer until the GPU has finished
        // using it from the previous frame.
        check(
            vkWaitForFences(
                device,
                1,
                &inFlight[currentFrame],
                VK_TRUE,
                UINT64_MAX),
            "Waiting for previous frame");

        uint32_t imageIndex{};
        const VkResult acquireResult = vkAcquireNextImageKHR(
            device,
            swapchain,
            UINT64_MAX,
            imageAvailable[currentFrame],
            VK_NULL_HANDLE,
            &imageIndex);

        // The swapchain is no longer compatible with the window, usually
        // because it was resized.  Recreate it and try again next frame.
        if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
            recreateSwapchain();
            return;
        }

        if (acquireResult != VK_SUCCESS &&
            acquireResult != VK_SUBOPTIMAL_KHR) {
            check(acquireResult, "Acquiring swapchain image");
        }

        check(
            vkResetFences(device, 1, &inFlight[currentFrame]),
            "Resetting fence");
        check(
            vkResetCommandBuffer(commandBuffers[currentFrame], 0),
            "Resetting command buffer");

        recordCommandBuffer(commandBuffers[currentFrame], imageIndex);

        // Wait until the acquired image is ready for color-attachment work.
        const VkPipelineStageFlags waitStage =
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

        VkSubmitInfo submitInfo{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &imageAvailable[currentFrame];
        submitInfo.pWaitDstStageMask = &waitStage;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &commandBuffers[currentFrame];
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &renderFinished[currentFrame];

        check(
            vkQueueSubmit(
                graphicsQueue,
                1,
                &submitInfo,
                inFlight[currentFrame]),
            "Submitting frame");

        // Presentation waits for rendering to signal that the image is ready.
        VkPresentInfoKHR presentInfo{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &renderFinished[currentFrame];
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &swapchain;
        presentInfo.pImageIndices = &imageIndex;

        const VkResult presentResult =
            vkQueuePresentKHR(presentQueue, &presentInfo);

        if (presentResult == VK_ERROR_OUT_OF_DATE_KHR ||
            presentResult == VK_SUBOPTIMAL_KHR ||
            resized) {
            recreateSwapchain();
        } else if (presentResult != VK_SUCCESS) {
            check(presentResult, "Presenting frame");
        }

        currentFrame = (currentFrame + 1) % kFramesInFlight;
    }

    void updateFps() {
        ++frameCount;

        const auto now = std::chrono::steady_clock::now();
        const float seconds =
            std::chrono::duration<float>(now - fpsStart).count();

        // Updating twice per second is enough for a title-bar counter and
        // avoids changing the title every single frame.
        if (seconds < 0.5f) {
            return;
        }

        const uint32_t framesPerSecond =
            static_cast<uint32_t>(frameCount / seconds);
        const std::string title =
            "WaveSim | FPS: " + std::to_string(framesPerSecond);
        glfwSetWindowTitle(window, title.c_str());

        fpsStart = now;
        frameCount = 0;
    }

    void mainLoop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            drawFrame();
            updateFps();
        }

        // Make sure the GPU is finished before cleanup destroys its objects.
        vkDeviceWaitIdle(device);
    }

    // --------------------------- Resize support ------------------------

    void destroySwapchain() {
        for (VkFramebuffer framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, nullptr);
        }
        framebuffers.clear();

        if (renderPass) {
            vkDestroyRenderPass(device, renderPass, nullptr);
        }
        renderPass = {};

        for (VkImageView imageView : imageViews) {
            vkDestroyImageView(device, imageView, nullptr);
        }
        imageViews.clear();

        if (swapchain) {
            vkDestroySwapchainKHR(device, swapchain, nullptr);
        }
        swapchain = {};
        swapchainImages.clear();
    }

    void recreateSwapchain() {
        int width{};
        int height{};
        glfwGetFramebufferSize(window, &width, &height);

        // A minimized window can have a framebuffer of size 0.  Wait until
        // it has a usable size before asking Vulkan to recreate its images.
        while (width == 0 || height == 0) {
            glfwWaitEvents();
            glfwGetFramebufferSize(window, &width, &height);
        }

        vkDeviceWaitIdle(device);
        destroySwapchain();
        createSwapchain();
        resized = false;
    }

    // ----------------------------- Cleanup ------------------------------

    void cleanup() {
        if (device) {
            vkDeviceWaitIdle(device);
        }

        for (uint32_t frameIndex = 0; frameIndex < kFramesInFlight;
             ++frameIndex) {
            if (inFlight[frameIndex]) {
                vkDestroyFence(device, inFlight[frameIndex], nullptr);
            }
            if (renderFinished[frameIndex]) {
                vkDestroySemaphore(device, renderFinished[frameIndex], nullptr);
            }
            if (imageAvailable[frameIndex]) {
                vkDestroySemaphore(device, imageAvailable[frameIndex], nullptr);
            }
        }

        destroySwapchain();

        if (commandPool) {
            vkDestroyCommandPool(device, commandPool, nullptr);
        }
        if (device) {
            vkDestroyDevice(device, nullptr);
        }
        if (surface) {
            vkDestroySurfaceKHR(instance, surface, nullptr);
        }
        if (instance) {
            vkDestroyInstance(instance, nullptr);
        }
        if (window) {
            glfwDestroyWindow(window);
        }

        glfwTerminate();
    }
};

class WaveField {
public:
    WaveField(int width, int height)
        : width_(width), height_(height) {
    }

private:
    int width_;
    int height_;

    struct DemoVertex {
        float x;
        float y;
        float red;
        float green;
        float blue;
    };

    const std::array<DemoVertex, 3> triangle = {{
        {0.0f, -0.6f, 1.0f, 0.2f, 0.2f},
        {0.6f,  0.6f, 0.2f, 1.0f, 0.2f},
        {-0.6f, 0.5f, 0.2f, 0.4f, 1.0f}
    }};

    createGraphicsPipeline(){
        
    }
};

} // namespace

int main() {
    try {
        App{}.run();
        return EXIT_SUCCESS;
    } catch (const std::exception& error) {
        std::cerr << "WaveSim error: " << error.what() << '\n';
        return EXIT_FAILURE;
    }
}
