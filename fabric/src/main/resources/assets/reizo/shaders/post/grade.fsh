#version 330

// Reizo image grade: exposure, contrast, saturation, temperature, vignette and letterbox.
// Written for Reizo (GPL-3.0-or-later).

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform ReizoGrade {
    vec4 Tone;   // exposure (stops), contrast, saturation, temperature
    vec4 Frame;  // vignette, letterbox bar (fraction of height), aspect (width / height), unused
};

out vec4 fragColor;

const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);

void main() {
    if (texCoord.y < Frame.y || texCoord.y > 1.0 - Frame.y) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 c = texture(InSampler, texCoord).rgb;
    c *= exp2(Tone.x);
    // Warm adds red and takes blue; cool does the reverse.
    c *= vec3(1.0 + 0.1 * Tone.w, 1.0, 1.0 - 0.1 * Tone.w);
    c = (c - 0.5) * Tone.y + 0.5;
    float l = dot(c, Luma);
    c = mix(vec3(l), c, Tone.z);
    if (Frame.x > 0.0) {
        vec2 d = (texCoord - 0.5) * vec2(Frame.z, 1.0);
        float r = length(d) / length(vec2(Frame.z, 1.0) * 0.5);
        c *= 1.0 - Frame.x * smoothstep(0.45, 1.0, r);
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
