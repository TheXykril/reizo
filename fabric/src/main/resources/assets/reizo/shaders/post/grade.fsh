#version 330

// Reizo image effects: depth of field, bloom, exposure, contrast, saturation, temperature,
// vignette and letterbox. Written for Reizo (GPL-3.0-or-later). 1.21.x header; the effect itself
// is in include/grade_body.glsl.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

#moj_import <reizo:grade_body.glsl>
