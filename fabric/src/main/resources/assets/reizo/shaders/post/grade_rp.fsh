#version 330
#extension GL_ARB_separate_shader_objects : require

// Reizo image effects, 26.x header (shaders are compiled to SPIR-V, so locations are explicit).
// The effect itself is in include/grade_body.glsl. Written for Reizo (GPL-3.0-or-later).

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

#include <reizo:grade_body.glsl>
