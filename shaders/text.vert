#version 450

layout(push_constant) uniform PushConstants {
    vec2 scale;
    vec2 translate;
} pushConstants;

layout(location = 0) out vec2 texCoord;

vec2 positions[6] = vec2[](
    vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
    vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
);

void main() {
    vec2 position = positions[gl_VertexIndex];
    gl_Position = vec4(position * pushConstants.scale + pushConstants.translate, 0.0, 1.0);
    texCoord = position;
}
