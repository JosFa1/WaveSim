cmake_minimum_required(VERSION 3.22)
project(WaveSim LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(Vulkan REQUIRED)
find_package(glfw3 3.3 CONFIG REQUIRED)

add_executable(wave_sim src/main.cpp)
target_link_libraries(wave_sim PRIVATE Vulkan::Vulkan glfw)

if(MSVC)
  target_compile_options(wave_sim PRIVATE /W4 /permissive-)
endif()
