// A minimal Vulkan + GLFW starting point. Add your own rendering in
// recordCommandBuffer() when you are ready.
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
constexpr uint32_t kFramesInFlight = 2;
[[noreturn]] void fail(const std::string& message) { throw std::runtime_error(message); }
void check(VkResult result, const char* action) {
    if (result != VK_SUCCESS) fail(std::string(action) + " failed: " + std::to_string(result));
}

struct QueueFamilies {
    std::optional<uint32_t> graphics;
    std::optional<uint32_t> present;
    bool complete() const { return graphics && present; }
};

class App {
public:
    void run() { createWindow(); createVulkan(); loop(); cleanup(); }

private:
    GLFWwindow* window{};
    VkInstance instance{};
    VkSurfaceKHR surface{};
    VkPhysicalDevice gpu{};
    VkDevice device{};
    VkQueue graphicsQueue{}, presentQueue{};
    VkSwapchainKHR swapchain{};
    VkFormat swapchainFormat{};
    VkExtent2D swapchainExtent{};
    std::vector<VkImage> swapchainImages;
    std::vector<VkImageView> imageViews;
    VkRenderPass renderPass{};
    std::vector<VkFramebuffer> framebuffers;
    VkCommandPool commandPool{};
    std::array<VkCommandBuffer, kFramesInFlight> commandBuffers{};
    std::array<VkSemaphore, kFramesInFlight> imageAvailable{}, renderFinished{};
    std::array<VkFence, kFramesInFlight> inFlight{};
    uint32_t frame{};
    bool resized{};
    uint32_t frameCount{};
    std::chrono::steady_clock::time_point fpsStart{};

    void createWindow() {
        if (!glfwInit()) fail("Could not initialize GLFW");
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(1100, 650, "WaveSim | FPS: --", nullptr, nullptr);
        if (!window) fail("Could not create the window");
        glfwSetWindowUserPointer(window, this);
        glfwSetFramebufferSizeCallback(window, [](GLFWwindow* w, int, int) {
            static_cast<App*>(glfwGetWindowUserPointer(w))->resized = true;
        });
        fpsStart = std::chrono::steady_clock::now();
    }

    void createVulkan() {
        createInstance();
        check(glfwCreateWindowSurface(instance, window, nullptr, &surface), "Creating window surface");
        pickGpu(); createDevice(); createCommandPool(); createSwapchain(); createCommandBuffers(); createSyncObjects();
    }

    void createInstance() {
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName = "WaveSim";
        app.apiVersion = VK_API_VERSION_1_1;
        uint32_t extensionCount{};
        const char** extensions = glfwGetRequiredInstanceExtensions(&extensionCount);
        VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        info.pApplicationInfo = &app;
        info.enabledExtensionCount = extensionCount;
        info.ppEnabledExtensionNames = extensions;
        check(vkCreateInstance(&info, nullptr, &instance), "Creating Vulkan instance");
    }

    QueueFamilies queuesFor(VkPhysicalDevice candidate) const {
        QueueFamilies queues;
        uint32_t count{};
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &count, nullptr);
        std::vector<VkQueueFamilyProperties> properties(count);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &count, properties.data());
        for (uint32_t i = 0; i < count; ++i) {
            if (properties[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) queues.graphics = i;
            VkBool32 supportsPresent{};
            vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &supportsPresent);
            if (supportsPresent) queues.present = i;
        }
        return queues;
    }

    bool supportsSwapchain(VkPhysicalDevice candidate) const {
        uint32_t count{};
        vkEnumerateDeviceExtensionProperties(candidate, nullptr, &count, nullptr);
        std::vector<VkExtensionProperties> extensions(count);
        vkEnumerateDeviceExtensionProperties(candidate, nullptr, &count, extensions.data());
        for (const auto& extension : extensions)
            if (std::string(extension.extensionName) == VK_KHR_SWAPCHAIN_EXTENSION_NAME) return true;
        return false;
    }

    void pickGpu() {
        uint32_t count{};
        vkEnumeratePhysicalDevices(instance, &count, nullptr);
        if (!count) fail("No Vulkan-capable GPU was found");
        std::vector<VkPhysicalDevice> candidates(count);
        vkEnumeratePhysicalDevices(instance, &count, candidates.data());
        for (auto candidate : candidates) {
            if (queuesFor(candidate).complete() && supportsSwapchain(candidate)) { gpu = candidate; return; }
        }
        fail("No GPU can draw to this window");
    }

    void createDevice() {
        const auto queues = queuesFor(gpu);
        std::vector<uint32_t> families{*queues.graphics};
        if (*queues.present != *queues.graphics) families.push_back(*queues.present);
        const float priority = 1.0f;
        std::vector<VkDeviceQueueCreateInfo> queueInfos;
        for (uint32_t family : families) {
            VkDeviceQueueCreateInfo queue{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
            queue.queueFamilyIndex = family; queue.queueCount = 1; queue.pQueuePriorities = &priority;
            queueInfos.push_back(queue);
        }
        const char* extensions[] = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        VkDeviceCreateInfo info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        info.queueCreateInfoCount = static_cast<uint32_t>(queueInfos.size()); info.pQueueCreateInfos = queueInfos.data();
        info.enabledExtensionCount = 1; info.ppEnabledExtensionNames = extensions;
        check(vkCreateDevice(gpu, &info, nullptr, &device), "Creating logical device");
        vkGetDeviceQueue(device, *queues.graphics, 0, &graphicsQueue);
        vkGetDeviceQueue(device, *queues.present, 0, &presentQueue);
    }

    VkSurfaceFormatKHR chooseFormat(const std::vector<VkSurfaceFormatKHR>& formats) const {
        for (const auto& format : formats)
            if (format.format == VK_FORMAT_B8G8R8A8_SRGB && format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) return format;
        return formats.front();
    }

    VkExtent2D chooseExtent(const VkSurfaceCapabilitiesKHR& capabilities) const {
        if (capabilities.currentExtent.width != std::numeric_limits<uint32_t>::max()) return capabilities.currentExtent;
        int width{}, height{};
        glfwGetFramebufferSize(window, &width, &height);
        return {std::clamp(static_cast<uint32_t>(width), capabilities.minImageExtent.width, capabilities.maxImageExtent.width),
                std::clamp(static_cast<uint32_t>(height), capabilities.minImageExtent.height, capabilities.maxImageExtent.height)};
    }

    void createSwapchain() {
        VkSurfaceCapabilitiesKHR capabilities{};
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, surface, &capabilities);
        uint32_t formatCount{};
        vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, &formatCount, nullptr);
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, &formatCount, formats.data());
        const auto format = chooseFormat(formats);
        swapchainFormat = format.format;
        swapchainExtent = chooseExtent(capabilities);
        uint32_t imageCount = capabilities.minImageCount + 1;
        if (capabilities.maxImageCount && imageCount > capabilities.maxImageCount) imageCount = capabilities.maxImageCount;
        const auto queues = queuesFor(gpu);
        const uint32_t indices[] = {*queues.graphics, *queues.present};
        VkSwapchainCreateInfoKHR info{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        info.surface = surface; info.minImageCount = imageCount; info.imageFormat = swapchainFormat; info.imageColorSpace = format.colorSpace;
        info.imageExtent = swapchainExtent; info.imageArrayLayers = 1; info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        if (*queues.graphics != *queues.present) { info.imageSharingMode = VK_SHARING_MODE_CONCURRENT; info.queueFamilyIndexCount = 2; info.pQueueFamilyIndices = indices; }
        else info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.preTransform = capabilities.currentTransform; info.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        info.presentMode = VK_PRESENT_MODE_FIFO_KHR; // Always available and synced to the display.
        info.clipped = VK_TRUE;
        check(vkCreateSwapchainKHR(device, &info, nullptr, &swapchain), "Creating swapchain");
        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, nullptr);
        swapchainImages.resize(imageCount);
        vkGetSwapchainImagesKHR(device, swapchain, &imageCount, swapchainImages.data());
        createImageViews(); createRenderPass(); createFramebuffers();
    }

    void createImageViews() {
        imageViews.resize(swapchainImages.size());
        for (size_t i = 0; i < swapchainImages.size(); ++i) {
            VkImageViewCreateInfo info{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            info.image = swapchainImages[i]; info.viewType = VK_IMAGE_VIEW_TYPE_2D; info.format = swapchainFormat;
            info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT; info.subresourceRange.levelCount = 1; info.subresourceRange.layerCount = 1;
            check(vkCreateImageView(device, &info, nullptr, &imageViews[i]), "Creating image view");
        }
    }

    void createRenderPass() {
        VkAttachmentDescription color{};
        color.format = swapchainFormat; color.samples = VK_SAMPLE_COUNT_1_BIT; color.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR; color.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        color.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED; color.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference reference{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS; subpass.colorAttachmentCount = 1; subpass.pColorAttachments = &reference;
        VkRenderPassCreateInfo info{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        info.attachmentCount = 1; info.pAttachments = &color; info.subpassCount = 1; info.pSubpasses = &subpass;
        check(vkCreateRenderPass(device, &info, nullptr, &renderPass), "Creating render pass");
    }

    void createFramebuffers() {
        framebuffers.resize(imageViews.size());
        for (size_t i = 0; i < imageViews.size(); ++i) {
            VkFramebufferCreateInfo info{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            info.renderPass = renderPass; info.attachmentCount = 1; info.pAttachments = &imageViews[i];
            info.width = swapchainExtent.width; info.height = swapchainExtent.height; info.layers = 1;
            check(vkCreateFramebuffer(device, &info, nullptr, &framebuffers[i]), "Creating framebuffer");
        }
    }

    void createCommandPool() {
        VkCommandPoolCreateInfo info{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        info.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT; info.queueFamilyIndex = *queuesFor(gpu).graphics;
        check(vkCreateCommandPool(device, &info, nullptr, &commandPool), "Creating command pool");
    }

    void createCommandBuffers() {
        VkCommandBufferAllocateInfo info{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        info.commandPool = commandPool; info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY; info.commandBufferCount = kFramesInFlight;
        check(vkAllocateCommandBuffers(device, &info, commandBuffers.data()), "Allocating command buffers");
    }

    void createSyncObjects() {
        VkSemaphoreCreateInfo semaphore{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        VkFenceCreateInfo fence{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO}; fence.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        for (uint32_t i = 0; i < kFramesInFlight; ++i) {
            check(vkCreateSemaphore(device, &semaphore, nullptr, &imageAvailable[i]), "Creating image semaphore");
            check(vkCreateSemaphore(device, &semaphore, nullptr, &renderFinished[i]), "Creating render semaphore");
            check(vkCreateFence(device, &fence, nullptr, &inFlight[i]), "Creating fence");
        }
    }

    void recordCommandBuffer(VkCommandBuffer command, uint32_t imageIndex) {
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        check(vkBeginCommandBuffer(command, &begin), "Beginning command buffer");

        // The intentionally empty screen. Add graphics pipelines and vkCmdDraw calls here later.
        VkClearValue clear{};
        clear.color = {{0.015f, 0.020f, 0.035f, 1.0f}};
        VkRenderPassBeginInfo render{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        render.renderPass = renderPass; render.framebuffer = framebuffers[imageIndex]; render.renderArea.extent = swapchainExtent;
        render.clearValueCount = 1; render.pClearValues = &clear;
        vkCmdBeginRenderPass(command, &render, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdEndRenderPass(command);
        check(vkEndCommandBuffer(command), "Ending command buffer");
    }

    void drawFrame() {
        check(vkWaitForFences(device, 1, &inFlight[frame], VK_TRUE, UINT64_MAX), "Waiting for previous frame");
        uint32_t imageIndex{};
        const VkResult acquired = vkAcquireNextImageKHR(device, swapchain, UINT64_MAX, imageAvailable[frame], VK_NULL_HANDLE, &imageIndex);
        if (acquired == VK_ERROR_OUT_OF_DATE_KHR) { recreateSwapchain(); return; }
        if (acquired != VK_SUCCESS && acquired != VK_SUBOPTIMAL_KHR) check(acquired, "Acquiring swapchain image");
        check(vkResetFences(device, 1, &inFlight[frame]), "Resetting fence");
        check(vkResetCommandBuffer(commandBuffers[frame], 0), "Resetting command buffer");
        recordCommandBuffer(commandBuffers[frame], imageIndex);
        const VkPipelineStageFlags waitStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.waitSemaphoreCount = 1; submit.pWaitSemaphores = &imageAvailable[frame]; submit.pWaitDstStageMask = &waitStage;
        submit.commandBufferCount = 1; submit.pCommandBuffers = &commandBuffers[frame];
        submit.signalSemaphoreCount = 1; submit.pSignalSemaphores = &renderFinished[frame];
        check(vkQueueSubmit(graphicsQueue, 1, &submit, inFlight[frame]), "Submitting frame");
        VkPresentInfoKHR present{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        present.waitSemaphoreCount = 1; present.pWaitSemaphores = &renderFinished[frame];
        present.swapchainCount = 1; present.pSwapchains = &swapchain; present.pImageIndices = &imageIndex;
        const VkResult presented = vkQueuePresentKHR(presentQueue, &present);
        if (presented == VK_ERROR_OUT_OF_DATE_KHR || presented == VK_SUBOPTIMAL_KHR || resized) recreateSwapchain();
        else if (presented != VK_SUCCESS) check(presented, "Presenting frame");
        frame = (frame + 1) % kFramesInFlight;
    }

    void updateFps() {
        ++frameCount;
        const auto now = std::chrono::steady_clock::now();
        const float seconds = std::chrono::duration<float>(now - fpsStart).count();
        if (seconds < 0.5f) return;
        glfwSetWindowTitle(window, ("WaveSim | FPS: " + std::to_string(static_cast<uint32_t>(frameCount / seconds))).c_str());
        fpsStart = now; frameCount = 0;
    }

    void loop() {
        while (!glfwWindowShouldClose(window)) { glfwPollEvents(); drawFrame(); updateFps(); }
        vkDeviceWaitIdle(device);
    }

    void destroySwapchain() {
        for (auto framebuffer : framebuffers) vkDestroyFramebuffer(device, framebuffer, nullptr);
        framebuffers.clear();
        if (renderPass) vkDestroyRenderPass(device, renderPass, nullptr);
        renderPass = {};
        for (auto view : imageViews) vkDestroyImageView(device, view, nullptr);
        imageViews.clear();
        if (swapchain) vkDestroySwapchainKHR(device, swapchain, nullptr);
        swapchain = {};
    }

    void recreateSwapchain() {
        int width{}, height{};
        glfwGetFramebufferSize(window, &width, &height);
        while (!width || !height) { glfwWaitEvents(); glfwGetFramebufferSize(window, &width, &height); }
        vkDeviceWaitIdle(device); destroySwapchain(); createSwapchain(); resized = false;
    }

    void cleanup() {
        if (device) vkDeviceWaitIdle(device);
        for (uint32_t i = 0; i < kFramesInFlight; ++i) {
            if (inFlight[i]) vkDestroyFence(device, inFlight[i], nullptr);
            if (renderFinished[i]) vkDestroySemaphore(device, renderFinished[i], nullptr);
            if (imageAvailable[i]) vkDestroySemaphore(device, imageAvailable[i], nullptr);
        }
        destroySwapchain();
        if (commandPool) vkDestroyCommandPool(device, commandPool, nullptr);
        if (device) vkDestroyDevice(device, nullptr);
        if (surface) vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance) vkDestroyInstance(instance, nullptr);
        if (window) glfwDestroyWindow(window);
        glfwTerminate();
    }
};
} // namespace

int main() {
    try { App{}.run(); return EXIT_SUCCESS; }
    catch (const std::exception& error) { std::cerr << "WaveSim error: " << error.what() << '\n'; return EXIT_FAILURE; }
}
