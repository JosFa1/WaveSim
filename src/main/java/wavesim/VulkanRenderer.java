package wavesim;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Owns the Vulkan objects needed to draw the WaveSim window.
 *
 * This class is intentionally focused on rendering. Window creation and the
 * application loop live in WindowManager and WaveSim, so a beginner can study
 * one responsibility at a time.
 *
 * The renderer clears the window and draws a small FPS texture. The next
 * drawing experiment belongs in recordCommandBuffer().
 */
public final class VulkanRenderer {
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 64;

    // WindowManager gives Vulkan access to the GLFW window without making
    // this renderer responsible for creating or destroying the window.
    private final WindowManager window;
    private VkInstance instance;
    private long surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private int graphicsFamily;
    private int presentFamily;

    private long swapchain;
    private int swapchainFormat;
    private VkExtent2D swapchainExtent;
    private long[] swapchainImages = new long[0];
    private long[] imageViews = new long[0];
    private long renderPass;
    private long[] framebuffers = new long[0];

    private long descriptorSetLayout;
    private long descriptorPool;
    private long descriptorSet;
    private long pipelineLayout;
    private long graphicsPipeline;

    private long commandPool;
    private VkCommandBuffer[] commandBuffers;
    private final long[] imageAvailable = new long[MAX_FRAMES_IN_FLIGHT];
    private long[] renderFinished = new long[0];
    private final long[] inFlight = new long[MAX_FRAMES_IN_FLIGHT];
    private long[] imagesInFlight = new long[0];
    private int currentFrame;

    private long textImage;
    private long textImageMemory;
    private long textImageView;
    private long textSampler;
    private boolean textImageInitialized;


    /**
     * Connects the renderer to an already-created GLFW window.
     */
    public VulkanRenderer(WindowManager window) {
        this.window = window;
    }

    /**
     * Creates Vulkan objects in the order they depend on one another.
     */
    public void initialize() {
        // Each later Vulkan object depends on the objects created before it.
        createInstance();
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            check(GLFWVulkan.glfwCreateWindowSurface(instance, window.handle(), null, pSurface),
                    "Creating window surface");
            surface = pSurface.get(0);
        }

        // Choose a physical GPU, then create the logical device used by the app.
        pickPhysicalDevice();
        createLogicalDevice();
        // The swapchain is the set of images that will be shown in the window.
        createCommandPool();
        createSwapchain();
        // A render pass describes how each frame starts and ends.
        createRenderPass();
        // The descriptor layout tells the shader how to access the text image.
        createDescriptorSetLayout();
        // The graphics pipeline combines the shaders and drawing rules.
        createGraphicsPipeline();
        // Create the image, sampler, and descriptor set for the FPS texture.
        createTextResources();
        createFramebuffers();
        createCommandBuffers();
        createSyncObjects();
    }

    // -------------------- Vulkan instance and device --------------------

    /**
     * Creates the Vulkan instance, which is the starting point for Vulkan.
     */
    private void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("WaveSim"))
                    .pEngineName(stack.UTF8("WaveSim"))
                    .apiVersion(VK_API_VERSION_1_0);

            PointerBuffer requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null) {
                throw new IllegalStateException("GLFW did not return Vulkan extensions");
            }

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(requiredExtensions);

            PointerBuffer pInstance = stack.mallocPointer(1);
            check(vkCreateInstance(createInfo, null, pInstance), "Creating Vulkan instance");
            instance = new VkInstance(pInstance.get(0), createInfo);
        }
    }

    /**
     * Finds a physical GPU that can render to our GLFW window.
     */
    private void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(instance, count, null),
                    "Enumerating physical devices");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan-capable GPU was found");
            }

            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(instance, count, devices),
                    "Reading physical devices");
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                QueueFamilies queues = findQueueFamilies(candidate);
                if (queues.complete() && supportsSwapchain(candidate)) {
                    physicalDevice = candidate;
                    graphicsFamily = queues.graphicsFamily;
                    presentFamily = queues.presentFamily;
                    return;
                }
            }
        }
        throw new IllegalStateException("No GPU can draw to this window");
    }

    private QueueFamilies findQueueFamilies(VkPhysicalDevice candidate) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, families);

            int graphics = -1;
            int present = -1;
            IntBuffer supportsPresent = stack.ints(0);
            for (int i = 0; i < families.capacity(); i++) {
                if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphics = i;
                }
                vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, supportsPresent);
                if (supportsPresent.get(0) != VK_FALSE) {
                    present = i;
                }
            }
            return new QueueFamilies(graphics, present);
        }
    }

    private boolean supportsSwapchain(VkPhysicalDevice candidate) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumerateDeviceExtensionProperties(candidate, (ByteBuffer) null, count, null),
                    "Enumerating device extensions");
            var extensions = org.lwjgl.vulkan.VkExtensionProperties.calloc(count.get(0), stack);
            check(vkEnumerateDeviceExtensionProperties(candidate, (ByteBuffer) null, count, extensions),
                    "Reading device extensions");
            for (int i = 0; i < extensions.capacity(); i++) {
                if (VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensions.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Creates the logical device and gets handles for the graphics queues.
     */
    private void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {
            int familyCount = graphicsFamily == presentFamily ? 1 : 2;
            VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(familyCount, stack);
            queues.get(0).sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(graphicsFamily).pQueuePriorities(stack.floats(1.0f));
            if (familyCount == 2) {
                queues.get(1).sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(presentFamily).pQueuePriorities(stack.floats(1.0f));
            }

            PointerBuffer extensions = stack.mallocPointer(1);
            extensions.put(0, memAddress(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)));
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queues)
                    .ppEnabledExtensionNames(extensions);
            VkDeviceCreateInfo.nqueueCreateInfoCount(createInfo.address(), familyCount);

            PointerBuffer pDevice = stack.mallocPointer(1);
            check(vkCreateDevice(physicalDevice, createInfo, null, pDevice), "Creating logical device");
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, graphicsFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);
            vkGetDeviceQueue(device, presentFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);
        }
    }

    // -------------------- Swapchain and pipeline --------------------

    /**
     * Creates the images that Vulkan presents to the window.
     */
    private void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities),
                    "Reading surface capabilities");

            IntBuffer formatCount = stack.ints(0);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null),
                    "Reading surface format count");
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats),
                    "Reading surface formats");

            VkSurfaceFormatKHR chosenFormat = chooseSurfaceFormat(formats);
            swapchainFormat = chosenFormat.format();
            VkExtent2D chosenExtent = chooseSwapchainExtent(capabilities, stack);
            if (swapchainExtent != null) {
                swapchainExtent.free();
            }
            swapchainExtent = VkExtent2D.calloc().set(chosenExtent);

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() != 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }
            int presentMode = choosePresentMode(stack);

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(swapchainFormat)
                    .imageColorSpace(chosenFormat.colorSpace())
                    .imageExtent(swapchainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true);

            if (graphicsFamily != presentFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                        .queueFamilyIndexCount(2)
                        .pQueueFamilyIndices(stack.ints(graphicsFamily, presentFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer pSwapchain = stack.longs(0);
            check(vkCreateSwapchainKHR(device, createInfo, null, pSwapchain), "Creating swapchain");
            swapchain = pSwapchain.get(0);

            IntBuffer actualCount = stack.ints(0);
            check(vkGetSwapchainImagesKHR(device, swapchain, actualCount, null), "Reading swapchain images");
            LongBuffer images = stack.mallocLong(actualCount.get(0));
            check(vkGetSwapchainImagesKHR(device, swapchain, actualCount, images), "Reading swapchain image handles");
            swapchainImages = new long[actualCount.get(0)];
            images.get(swapchainImages);
            imagesInFlight = new long[swapchainImages.length];
            createImageViews();
        }
    }

    private VkSurfaceFormatKHR chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                    format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
        return formats.get(0);
    }

    private int choosePresentMode(MemoryStack stack) {
        IntBuffer modeCount = stack.ints(0);
        check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, null),
                "Reading present mode count");
        IntBuffer modes = stack.mallocInt(modeCount.get(0));
        check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, modeCount, modes),
                "Reading present modes");
        for (int i = 0; i < modes.capacity(); i++) {
            if (modes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                return VK_PRESENT_MODE_IMMEDIATE_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private VkExtent2D chooseSwapchainExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
            return VkExtent2D.calloc(stack).set(capabilities.currentExtent());
        }
        IntBuffer width = stack.ints(0);
        IntBuffer height = stack.ints(0);
        window.getFramebufferSize(width, height);
        return VkExtent2D.calloc(stack)
                .width(clamp(width.get(0), capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()))
                .height(clamp(height.get(0), capabilities.minImageExtent().height(), capabilities.maxImageExtent().height()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void createImageViews() {
        imageViews = new long[swapchainImages.length];
        try (MemoryStack stack = stackPush()) {
            for (int i = 0; i < swapchainImages.length; i++) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(swapchainImages[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(swapchainFormat);
                createInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1);
                LongBuffer pImageView = stack.longs(0);
                check(vkCreateImageView(device, createInfo, null, pImageView), "Creating image view");
                imageViews[i] = pImageView.get(0);
            }
        }
    }

    /**
     * Describes the color attachment used by each frame.
     */
    private void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(swapchainFormat).samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            VkAttachmentReference.Buffer colorReference = VkAttachmentReference.calloc(1, stack)
                    .attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            var subpass = org.lwjgl.vulkan.VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1).pColorAttachments(colorReference);
            VkRenderPassCreateInfo createInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(colorAttachment).pSubpasses(subpass);
            VkRenderPassCreateInfo.nattachmentCount(createInfo.address(), 1);
            VkRenderPassCreateInfo.nsubpassCount(createInfo.address(), 1);
            LongBuffer pRenderPass = stack.longs(0);
            check(vkCreateRenderPass(device, createInfo, null, pRenderPass), "Creating render pass");
            renderPass = pRenderPass.get(0);
        }
    }

    private void createDescriptorSetLayout() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                    .binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(binding);
            VkDescriptorSetLayoutCreateInfo.nbindingCount(createInfo.address(), 1);
            LongBuffer pLayout = stack.longs(0);
            check(vkCreateDescriptorSetLayout(device, createInfo, null, pLayout), "Creating descriptor set layout");
            descriptorSetLayout = pLayout.get(0);
        }
    }

    /**
     * Builds the shader pipeline used to draw the FPS texture.
     */
    private void createGraphicsPipeline() {
        ByteBuffer vertexCode = loadShader("/shaders/text.vert.spv");
        ByteBuffer fragmentCode = loadShader("/shaders/text.frag.spv");
        try (MemoryStack stack = stackPush()) {
            long vertexShader = createShaderModule(vertexCode, stack);
            long fragmentShader = createShaderModule(fragmentCode, stack);

            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexShader).pName(stack.UTF8("main"));
            stages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentShader).pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST).primitiveRestartEnable(false);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1).scissorCount(1);
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false).rasterizerDiscardEnable(false).polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f).cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineColorBlendAttachmentState.Buffer blendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .blendEnable(true).srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA).colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD).colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                            VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            VkPipelineColorBlendStateCreateInfo blending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(false).pAttachments(blendAttachment);
            IntBuffer dynamicStates = stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO).pDynamicStates(dynamicStates);

            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT).offset(0).size(16);
            VkPipelineLayoutCreateInfo layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout)).pPushConstantRanges(pushConstants);
            LongBuffer pLayout = stack.longs(0);
            check(vkCreatePipelineLayout(device, layoutInfo, null, pLayout), "Creating pipeline layout");
            pipelineLayout = pLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO).stageCount(2).pStages(stages)
                    .pVertexInputState(vertexInput).pInputAssemblyState(inputAssembly)
                    .pViewportState(viewport).pRasterizationState(rasterizer).pMultisampleState(multisampling)
                    .pColorBlendState(blending).pDynamicState(dynamic)
                    .layout(pipelineLayout).renderPass(renderPass).subpass(0);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline),
                    "Creating graphics pipeline");
            graphicsPipeline = pPipeline.get(0);
            vkDestroyShaderModule(device, vertexShader, null);
            vkDestroyShaderModule(device, fragmentShader, null);
        } finally {
            memFree(vertexCode);
            memFree(fragmentCode);
        }
    }

    // -------------------- Text texture --------------------

    private long createShaderModule(ByteBuffer code, MemoryStack stack) {
        VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO).pCode(code);
        LongBuffer pShader = stack.longs(0);
        check(vkCreateShaderModule(device, createInfo, null, pShader), "Creating shader module");
        return pShader.get(0);
    }

    private ByteBuffer loadShader(String resource) {
        try (InputStream input = WaveSim.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing shader resource: " + resource);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            input.transferTo(bytes);
            byte[] data = bytes.toByteArray();
            ByteBuffer buffer = memAlloc(data.length);
            buffer.put(data).flip();
            return buffer;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read shader: " + resource, error);
        }
    }

    private void createTextResources() {
        try (MemoryStack stack = stackPush()) {
            createImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, VK_FORMAT_R8G8B8A8_UNORM,
                    VK_IMAGE_TILING_OPTIMAL,
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            textImageView = createImageView(textImage, VK_FORMAT_R8G8B8A8_UNORM);

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR).minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false).maxAnisotropy(1.0f)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false).compareEnable(false)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);
            LongBuffer pSampler = stack.longs(0);
            check(vkCreateSampler(device, samplerInfo, null, pSampler), "Creating text sampler");
            textSampler = pSampler.get(0);
        }

        createDescriptorResources();
        updateFpsText("FPS: --");
    }

    // -------------------- Frame execution --------------------

    private void createImage(int width, int height, int format, int tiling, int usage, int properties) {
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO).imageType(VK_IMAGE_TYPE_2D)
                    .format(format).extent(e -> e.width(width).height(height).depth(1))
                    .mipLevels(1).arrayLayers(1).samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(tiling).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            LongBuffer pImage = stack.longs(0);
            check(vkCreateImage(device, imageInfo, null, pImage), "Creating text image");
            textImage = pImage.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, textImage, requirements);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), properties));
            LongBuffer pMemory = stack.longs(0);
            check(vkAllocateMemory(device, allocateInfo, null, pMemory), "Allocating text image memory");
            textImageMemory = pMemory.get(0);
            check(vkBindImageMemory(device, textImage, textImageMemory, 0), "Binding text image memory");
        }
    }

    private long createImageView(long image, int format) {
        try (MemoryStack stack = stackPush()) {
            VkImageViewCreateInfo info = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO).image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
            info.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            LongBuffer pView = stack.longs(0);
            check(vkCreateImageView(device, info, null, pView), "Creating text image view");
            return pView.get(0);
        }
    }

    private void createDescriptorResources() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1);
            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .maxSets(1).pPoolSizes(poolSize);
            VkDescriptorPoolCreateInfo.npoolSizeCount(poolInfo.address(), 1);
            LongBuffer pPool = stack.longs(0);
            check(vkCreateDescriptorPool(device, poolInfo, null, pPool), "Creating descriptor pool");
            descriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            VkDescriptorSetAllocateInfo.ndescriptorSetCount(allocateInfo.address(), 1);
            LongBuffer pSet = stack.longs(0);
            check(vkAllocateDescriptorSets(device, allocateInfo, pSet), "Allocating descriptor set");
            descriptorSet = pSet.get(0);
            updateDescriptorSet(stack);
        }
    }

    private void updateDescriptorSet(MemoryStack stack) {
        VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(textSampler).imageView(textImageView).imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET).dstSet(descriptorSet)
                .dstBinding(0).descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(imageInfo);
        vkUpdateDescriptorSets(device, write, null);
    }

    private void createFramebuffers() {
        framebuffers = new long[imageViews.length];
        try (MemoryStack stack = stackPush()) {
            for (int i = 0; i < imageViews.length; i++) {
                VkFramebufferCreateInfo info = VkFramebufferCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO).renderPass(renderPass)
                        .attachmentCount(1).pAttachments(stack.longs(imageViews[i])).width(swapchainExtent.width())
                        .height(swapchainExtent.height()).layers(1);
                LongBuffer pFramebuffer = stack.longs(0);
                check(vkCreateFramebuffer(device, info, null, pFramebuffer), "Creating framebuffer");
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    /**
     * Creates the command pool used to allocate command buffers.
     */
    private void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo info = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(graphicsFamily);
            LongBuffer pPool = stack.longs(0);
            check(vkCreateCommandPool(device, info, null, pPool), "Creating command pool");
            commandPool = pPool.get(0);
        }
    }

    /**
     * Allocates one reusable command buffer for each frame slot.
     */
    private void createCommandBuffers() {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo info = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO).commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(MAX_FRAMES_IN_FLIGHT);
            PointerBuffer pBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            check(vkAllocateCommandBuffers(device, info, pBuffers), "Allocating command buffers");
            commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = new VkCommandBuffer(pBuffers.get(i), device);
            }
        }
    }

    /**
     * Creates semaphores and fences used to safely overlap CPU and GPU work.
     */
    private void createSyncObjects() {
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO).flags(VK_FENCE_CREATE_SIGNALED_BIT);
            LongBuffer pHandle = stack.longs(0);
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                check(vkCreateSemaphore(device, semaphoreInfo, null, pHandle), "Creating image semaphore");
                imageAvailable[i] = pHandle.get(0);
                check(vkCreateFence(device, fenceInfo, null, pHandle), "Creating fence");
                inFlight[i] = pHandle.get(0);
            }
            createRenderFinishedSemaphores(semaphoreInfo, pHandle);
        }
    }

    private void createRenderFinishedSemaphores(VkSemaphoreCreateInfo semaphoreInfo, LongBuffer pHandle) {
        renderFinished = new long[swapchainImages.length];
        for (int i = 0; i < renderFinished.length; i++) {
            check(vkCreateSemaphore(device, semaphoreInfo, null, pHandle), "Creating render semaphore");
            renderFinished[i] = pHandle.get(0);
        }
    }

    private void destroyRenderFinishedSemaphores() {
        if (device == null) return;
        for (long semaphore : renderFinished) {
            if (semaphore != VK_NULL_HANDLE) vkDestroySemaphore(device, semaphore, null);
        }
        renderFinished = new long[0];
    }

    /**
     * Records the commands for one swapchain image.
     *
     * This is the most useful place for a future drawing experiment. Add
     * pipeline binds and vkCmdDraw calls here when you are ready.
     */
    private void recordCommandBuffer(VkCommandBuffer commandBuffer, int imageIndex) {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            check(vkBeginCommandBuffer(commandBuffer, beginInfo), "Beginning command buffer");

            VkClearValue.Buffer clearColors = VkClearValue.calloc(1, stack);
            clearColors.get(0).color().float32(0, 0.015f).float32(1, 0.020f)
                    .float32(2, 0.035f).float32(3, 1.0f);
            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO).renderPass(renderPass)
                    .framebuffer(framebuffers[imageIndex]).renderArea(a -> a.extent(swapchainExtent))
                    .clearValueCount(1).pClearValues(clearColors);
            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0).x(0).y(0).width(swapchainExtent.width()).height(swapchainExtent.height())
                    .minDepth(0).maxDepth(1);
            vkCmdSetViewport(commandBuffer, 0, viewport);
            var scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
            scissor.get(0).offset(o -> o.set(0, 0)).extent(swapchainExtent);
            vkCmdSetScissor(commandBuffer, 0, scissor);
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout,
                    0, stack.longs(descriptorSet), null);

            float scaleX = 2.0f * TEXTURE_WIDTH / swapchainExtent.width();
            float scaleY = 2.0f * TEXTURE_HEIGHT / swapchainExtent.height();
            float marginX = 2.0f * 12.0f / swapchainExtent.width();
            float marginY = 2.0f * 12.0f / swapchainExtent.height();
            ByteBuffer pushConstants = stack.malloc(16)
                    .putFloat(scaleX).putFloat(scaleY)
                    .putFloat(-1.0f + marginX).putFloat(-1.0f + marginY).flip();
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstants);
            vkCmdDraw(commandBuffer, 6, 1, 0, 0);
            vkCmdEndRenderPass(commandBuffer);
            check(vkEndCommandBuffer(commandBuffer), "Ending command buffer");
        }
    }

    /**
     * Draws one frame: acquire an image, submit commands, then present it.
     */
    public void drawFrame() {
        try (MemoryStack stack = stackPush()) {
            check(vkWaitForFences(device, inFlight[currentFrame], true, -1L),
                    "Waiting for previous frame");
            IntBuffer pImageIndex = stack.ints(0);
            int acquireResult = vkAcquireNextImageKHR(device, swapchain, -1L,
                    imageAvailable[currentFrame], VK_NULL_HANDLE, pImageIndex);
            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchain();
                return;
            }
            checkAllowed(acquireResult, "Acquiring swapchain image", VK_SUCCESS, VK_SUBOPTIMAL_KHR);
            int imageIndex = pImageIndex.get(0);

            if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
                check(vkWaitForFences(device, imagesInFlight[imageIndex], true, -1L),
                        "Waiting for swapchain image");
            }
            imagesInFlight[imageIndex] = inFlight[currentFrame];
            check(vkResetFences(device, inFlight[currentFrame]), "Resetting fence");
            check(vkResetCommandBuffer(commandBuffers[currentFrame], 0), "Resetting command buffer");
            recordCommandBuffer(commandBuffers[currentFrame], imageIndex);

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .waitSemaphoreCount(1).pWaitSemaphores(stack.longs(imageAvailable[currentFrame]))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffers[currentFrame].address()))
                    .pSignalSemaphores(stack.longs(renderFinished[imageIndex]));
            VkSubmitInfo.ncommandBufferCount(submit.address(), 1);
            VkSubmitInfo.nsignalSemaphoreCount(submit.address(), 1);
            check(vkQueueSubmit(graphicsQueue, submit, inFlight[currentFrame]), "Submitting frame");

            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(renderFinished[imageIndex]))
                    .swapchainCount(1).pSwapchains(stack.longs(swapchain)).pImageIndices(pImageIndex);
            VkPresentInfoKHR.nwaitSemaphoreCount(present.address(), 1);
            int presentResult = vkQueuePresentKHR(presentQueue, present);
            if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || window.wasResized()) {
                recreateSwapchain();
            } else {
                check(presentResult, "Presenting frame");
            }
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    /**
     * Waits until the GPU has finished all submitted work.
     */
    public void waitForIdle() {
        if (device != null) {
            check(vkDeviceWaitIdle(device), "Waiting for the device");
        }
    }

    /**
     * Rebuilds the small texture that displays the FPS counter.
     */
    public void updateFpsText(String text) {
        // Create an ARGB image that will hold the updated FPS overlay.
        BufferedImage image = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        // Clear the previous overlay so transparent pixels remain transparent.
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // Draw a semi-transparent background behind the FPS text.
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(new Color(0, 0, 0, 165));
        graphics.fillRoundRect(0, 0, 128, 48, 8, 8);

        // Configure readable text rendering and draw the supplied FPS value.
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.drawString(text, 12, 32);
        graphics.dispose();

        // Allocate a native RGBA buffer for uploading the image to Vulkan.
        ByteBuffer pixels = memAlloc(TEXTURE_WIDTH * TEXTURE_HEIGHT * 4);
        try {
            for (int y = 0; y < TEXTURE_HEIGHT; y++) {
                for (int x = 0; x < TEXTURE_WIDTH; x++) {
                    // Java stores pixels as ARGB; Vulkan expects the channels in RGBA order.
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) ((argb >>> 16) & 0xFF));
                    pixels.put((byte) ((argb >>> 8) & 0xFF));
                    pixels.put((byte) (argb & 0xFF));
                    pixels.put((byte) ((argb >>> 24) & 0xFF));
                }
            }

            // Prepare the buffer for reading before passing it to the upload routine.
            pixels.flip();
            uploadTextPixels(pixels);
        } finally {
            // Always release the native memory, including when uploading fails.
            memFree(pixels);
        }
    }

    private void uploadTextPixels(ByteBuffer pixels) {
        BufferResource staging = createBuffer(pixels.remaining(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        try (MemoryStack stack = stackPush()) {
            PointerBuffer mapped = stack.mallocPointer(1);
            check(vkMapMemory(device, staging.memory, 0, pixels.remaining(), 0, mapped), "Mapping text staging buffer");
            memByteBuffer(mapped.get(0), pixels.remaining()).put(pixels.duplicate());
            vkUnmapMemory(device, staging.memory);
        }

        vkDeviceWaitIdle(device);
        int oldLayout = textImageInitialized
                ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                : VK_IMAGE_LAYOUT_UNDEFINED;
        transitionImageLayout(textImage, oldLayout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        copyBufferToImage(staging.buffer, textImage, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        transitionImageLayout(textImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        textImageInitialized = true;
        vkDestroyBuffer(device, staging.buffer, null);
        vkFreeMemory(device, staging.memory, null);
    }

    private BufferResource createBuffer(long size, int usage, int properties) {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO).size(size)
                    .usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer pBuffer = stack.longs(0);
            check(vkCreateBuffer(device, info, null, pBuffer), "Creating buffer");
            long buffer = pBuffer.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO).allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), properties));
            LongBuffer pMemory = stack.longs(0);
            check(vkAllocateMemory(device, allocate, null, pMemory), "Allocating buffer memory");
            long memory = pMemory.get(0);
            check(vkBindBufferMemory(device, buffer, memory, 0), "Binding buffer memory");
            return new BufferResource(buffer, memory);
        }
    }

    private int findMemoryType(int typeBits, int requiredProperties) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0 &&
                        (properties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("No suitable Vulkan memory type was found");
    }

    private void transitionImageLayout(long image, int oldLayout, int newLayout) {
        VkCommandBuffer commandBuffer = beginSingleTimeCommands();
        try (MemoryStack stack = stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(1, stack);
            VkImageMemoryBarrier barrier = barriers.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER).oldLayout(oldLayout).newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            int sourceStage;
            int destinationStage;
            if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                barrier.srcAccessMask(VK_ACCESS_SHADER_READ_BIT).dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else {
                throw new IllegalArgumentException("Unsupported image layout transition");
            }
            vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
                    null, null, barriers);
        }
        endSingleTimeCommands(commandBuffer);
    }

    private void copyBufferToImage(long buffer, long image, int width, int height) {
        VkCommandBuffer commandBuffer = beginSingleTimeCommands();
        try (MemoryStack stack = stackPush()) {
            var region = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0)
                            .baseArrayLayer(0).layerCount(1))
                    .imageOffset(o -> o.set(0, 0, 0)).imageExtent(e -> e.set(width, height, 1));
            vkCmdCopyBufferToImage(commandBuffer, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
        }
        endSingleTimeCommands(commandBuffer);
    }

    private VkCommandBuffer beginSingleTimeCommands() {
        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandPool(commandPool).commandBufferCount(1);
            PointerBuffer pCommand = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, allocate, pCommand), "Allocating one-time command buffer");
            VkCommandBuffer commandBuffer = new VkCommandBuffer(pCommand.get(0), device);
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO).flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(commandBuffer, begin), "Beginning one-time command buffer");
            return commandBuffer;
        }
    }

    private void endSingleTimeCommands(VkCommandBuffer commandBuffer) {
        try (MemoryStack stack = stackPush()) {
            check(vkEndCommandBuffer(commandBuffer), "Ending one-time command buffer");
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(stack.pointers(commandBuffer.address()));
            check(vkQueueSubmit(graphicsQueue, submit, VK_NULL_HANDLE), "Submitting one-time command buffer");
            check(vkQueueWaitIdle(graphicsQueue), "Waiting for one-time command buffer");
            vkFreeCommandBuffers(device, commandPool, commandBuffer);
        }
    }

    private void recreateSwapchain() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);
            window.getFramebufferSize(width, height);
            // A minimized window has a framebuffer size of zero. Vulkan cannot
        // create a zero-sized swapchain, so wait until the window is visible.
        while (width.get(0) == 0 || height.get(0) == 0) {
                window.waitForEvents();
                window.getFramebufferSize(width, height);
            }
        }
        check(vkDeviceWaitIdle(device), "Waiting for device during resize");
        destroyRenderFinishedSemaphores();
        destroyFramebuffers();
        destroyGraphicsPipeline();
        vkDestroyRenderPass(device, renderPass, null);
        renderPass = VK_NULL_HANDLE;
        for (long imageView : imageViews) {
            vkDestroyImageView(device, imageView, null);
        }
        vkDestroySwapchainKHR(device, swapchain, null);
        imageViews = new long[0];
        swapchainImages = new long[0];
        createSwapchain();
        createRenderPass();
        createGraphicsPipeline();
        createFramebuffers();
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            createRenderFinishedSemaphores(semaphoreInfo, stack.longs(0));
        }
        window.clearResized();
    }

    private void destroyFramebuffers() {
        if (device == null) return;
        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }
        framebuffers = new long[0];
    }

    private void destroyGraphicsPipeline() {
        if (device == null) return;
        if (graphicsPipeline != VK_NULL_HANDLE) vkDestroyPipeline(device, graphicsPipeline, null);
        if (pipelineLayout != VK_NULL_HANDLE) vkDestroyPipelineLayout(device, pipelineLayout, null);
        graphicsPipeline = VK_NULL_HANDLE;
        pipelineLayout = VK_NULL_HANDLE;
    }

    /**
     * Releases Vulkan resources. It is safe to call this after a partial
     * initialization because the handles are checked before destruction.
     */
    public void cleanup() {
        if (device != null) {
            vkDeviceWaitIdle(device);
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (inFlight[i] != VK_NULL_HANDLE) vkDestroyFence(device, inFlight[i], null);
                if (imageAvailable[i] != VK_NULL_HANDLE) vkDestroySemaphore(device, imageAvailable[i], null);
            }
            destroyRenderFinishedSemaphores();
            destroyFramebuffers();
            destroyGraphicsPipeline();
            if (descriptorPool != VK_NULL_HANDLE) vkDestroyDescriptorPool(device, descriptorPool, null);
            if (descriptorSetLayout != VK_NULL_HANDLE) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            if (textSampler != VK_NULL_HANDLE) vkDestroySampler(device, textSampler, null);
            if (textImageView != VK_NULL_HANDLE) vkDestroyImageView(device, textImageView, null);
            if (textImage != VK_NULL_HANDLE) vkDestroyImage(device, textImage, null);
            if (textImageMemory != VK_NULL_HANDLE) vkFreeMemory(device, textImageMemory, null);
            if (renderPass != VK_NULL_HANDLE) vkDestroyRenderPass(device, renderPass, null);
            for (long imageView : imageViews) vkDestroyImageView(device, imageView, null);
            if (swapchain != VK_NULL_HANDLE) vkDestroySwapchainKHR(device, swapchain, null);
            if (commandPool != VK_NULL_HANDLE) vkDestroyCommandPool(device, commandPool, null);
            vkDestroyDevice(device, null);
        }
        if (instance != null) {
            if (surface != VK_NULL_HANDLE) vkDestroySurfaceKHR(instance, surface, null);
            vkDestroyInstance(instance, null);
        }
        if (swapchainExtent != null) {
            swapchainExtent.free();
            swapchainExtent = null;
        }
    }

    // Vulkan reports errors as integer result codes. Keeping this check in
    // one place makes the rest of the renderer easier to read.
    private static void check(int result, String action) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(action + " failed with VkResult " + result);
        }
    }

    private static void checkAllowed(int result, String action, int... allowed) {
        for (int value : allowed) if (result == value) return;
        check(result, action);
    }

    private record QueueFamilies(int graphicsFamily, int presentFamily) {
        boolean complete() { return graphicsFamily >= 0 && presentFamily >= 0; }
    }

    private record BufferResource(long buffer, long memory) {}
}
