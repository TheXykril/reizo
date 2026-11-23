// Reizo image effects, shared by post/grade.fsh (1.21.x) and post/grade_rp.fsh (26.x).
// Written for Reizo (GPL-3.0-or-later). The including shader declares InSampler, DepthSampler,
// texCoord and fragColor.

layout(std140) uniform ReizoGrade {
    vec4 Tone;   // exposure (stops), contrast, saturation, temperature
    vec4 Frame;  // vignette, letterbox bar (fraction of height), aspect (width / height), unused
    vec4 Lens;   // bloom, depth of field strength, focus distance (blocks), unused
    vec4 Screen; // texel width, texel height, depth-0 plane, depth-1 plane (26.x swaps near and far)
};

const vec3 Luma = vec3(0.2126, 0.7152, 0.0722);
const float GoldenAngle = 2.39996323;
const int DofTaps = 24;

float linearDepth(vec2 uv) {
    float d = texture(DepthSampler, uv).r * 2.0 - 1.0;
    float n = Screen.z;
    float f = Screen.w;
    return 2.0 * n * f / (f + n - d * (f - n));
}

// Blur radius in pixels: 0 in focus, up to 1/60 of the height at full strength.
float blurRadius(float depth) {
    float maxRadius = Lens.y / (60.0 * Screen.y);
    return maxRadius * clamp(abs(depth - Lens.z) / max(depth, 0.001), 0.0, 1.0);
}

vec3 depthOfField(vec3 sharp) {
    float centre = blurRadius(linearDepth(texCoord));
    if (centre < 0.5) {
        return sharp;
    }
    vec3 sum = sharp;
    float weight = 1.0;
    for (int i = 1; i <= DofTaps; i++) {
        float r = centre * sqrt(float(i) / float(DofTaps));
        float a = float(i) * GoldenAngle;
        vec2 uv = texCoord + vec2(cos(a), sin(a)) * r * Screen.xy;
        // A sharper sample in front would bleed into the blur; count it less.
        float w = clamp(blurRadius(linearDepth(uv)) / centre, 0.1, 1.0);
        sum += texture(InSampler, uv).rgb * w;
        weight += w;
    }
    return sum / weight;
}

// Light from the bright parts of the picture spread around them. Brightness is judged by luminance
// with a soft threshold, so colors keep their hue. Each pixel turns its sample pattern by a
// different angle, which hides the rings a fixed pattern would draw.
vec3 bloom() {
    vec3 sum = vec3(0.0);
    float total = 0.0;
    float unit = 1.0 / (240.0 * Screen.y);
    float turn = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831853;
    for (int ring = 1; ring <= 4; ring++) {
        float r = unit * float(ring * ring + 1);
        float w = 1.0 / float(ring);
        for (int i = 0; i < 8; i++) {
            float a = turn + (float(i) + 0.5 * float(ring)) * 0.78539816;
            vec3 s = texture(InSampler, texCoord + vec2(cos(a), sin(a)) * r * Screen.xy).rgb;
            float l = dot(s, Luma);
            float knee = smoothstep(0.75, 1.0, l);
            sum += s * knee * w;
            total += w;
        }
    }
    return sum / total * Lens.x * 1.5;
}

void main() {
    if (texCoord.y < Frame.y || texCoord.y > 1.0 - Frame.y) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 c = texture(InSampler, texCoord).rgb;
    if (Lens.y > 0.0) {
        c = depthOfField(c);
    }
    if (Lens.x > 0.0) {
        c += bloom();
    }
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
