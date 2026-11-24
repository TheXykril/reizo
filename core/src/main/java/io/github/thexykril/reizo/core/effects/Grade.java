/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.effects;

/**
 * Image effects applied to a replay's picture in the editor and in exports. Every value has a
 * neutral setting; a neutral grade costs nothing (no pass runs).
 *
 * @param exposure brightness in stops, -3 to 3 (0 neutral)
 * @param contrast 0 to 2 (1 neutral)
 * @param saturation 0 (gray) to 2 (1 neutral)
 * @param temperature -1 (cool, blue) to 1 (warm, orange); 0 neutral
 * @param vignette darkening of the corners, 0 to 1 (0 neutral)
 * @param letterbox aspect ratio of the picture inside black bars (for example 2.39); 0 for none
 */
public record Grade(
    float exposure,
    float contrast,
    float saturation,
    float temperature,
    float vignette,
    float letterbox,
    float bloom,
    float dof,
    float focus) {
  /**
   * Focus distance meaning "automatic": depth of field focuses on whatever is at the centre of the
   * picture, like a camera's autofocus.
   */
  public static final float AUTO_FOCUS = 0;

  /** Focus distance in blocks when none is set: automatic. */
  public static final float DEFAULT_FOCUS = AUTO_FOCUS;

  public static final Grade NEUTRAL = new Grade(0, 1, 1, 0, 0, 0);

  /** Letterbox ratios offered in the editor. */
  public static final float[] LETTERBOXES = {0, 1.85f, 2f, 2.39f};

  public Grade {
    exposure = clamp(exposure, -3, 3);
    contrast = clamp(contrast, 0, 2);
    saturation = clamp(saturation, 0, 2);
    temperature = clamp(temperature, -1, 1);
    vignette = clamp(vignette, 0, 1);
    letterbox = letterbox <= 0 ? 0 : clamp(letterbox, 1, 4);
    bloom = clamp(bloom, 0, 1);
    dof = clamp(dof, 0, 1);
    focus =
        Float.isNaN(focus) ? DEFAULT_FOCUS : focus <= 0.25f ? AUTO_FOCUS : clamp(focus, 0.5f, 256);
  }

  /** A grade without bloom or depth of field. */
  public Grade(
      float exposure,
      float contrast,
      float saturation,
      float temperature,
      float vignette,
      float letterbox) {
    this(exposure, contrast, saturation, temperature, vignette, letterbox, 0, 0, DEFAULT_FOCUS);
  }

  private static float clamp(float v, float min, float max) {
    return Float.isNaN(v) ? min : Math.max(min, Math.min(max, v));
  }

  /**
   * Whether the grade changes nothing. The focus distance alone changes nothing while depth of
   * field is off.
   */
  public boolean isNeutral() {
    return exposure == 0
        && contrast == 1
        && saturation == 1
        && temperature == 0
        && vignette == 0
        && letterbox == 0
        && bloom == 0
        && dof == 0;
  }

  /**
   * Linear blend from this grade to {@code to}. The letterbox does not blend: bars that grow from
   * one ratio to another look like a glitch, so it switches at the end.
   */
  public Grade lerp(Grade to, double f) {
    float t = (float) Math.max(0, Math.min(1, f));
    return new Grade(
        exposure + (to.exposure - exposure) * t,
        contrast + (to.contrast - contrast) * t,
        saturation + (to.saturation - saturation) * t,
        temperature + (to.temperature - temperature) * t,
        vignette + (to.vignette - vignette) * t,
        t >= 1 ? to.letterbox : letterbox,
        bloom + (to.bloom - bloom) * t,
        dof + (to.dof - dof) * t,
        // Automatic focus has no distance to blend from or to, so it switches at the end.
        focus == AUTO_FOCUS || to.focus == AUTO_FOCUS
            ? (t >= 1 ? to.focus : focus)
            : focus + (to.focus - focus) * t);
  }

  public Grade withExposure(float v) {
    return new Grade(v, contrast, saturation, temperature, vignette, letterbox, bloom, dof, focus);
  }

  public Grade withContrast(float v) {
    return new Grade(exposure, v, saturation, temperature, vignette, letterbox, bloom, dof, focus);
  }

  public Grade withSaturation(float v) {
    return new Grade(exposure, contrast, v, temperature, vignette, letterbox, bloom, dof, focus);
  }

  public Grade withTemperature(float v) {
    return new Grade(exposure, contrast, saturation, v, vignette, letterbox, bloom, dof, focus);
  }

  public Grade withVignette(float v) {
    return new Grade(exposure, contrast, saturation, temperature, v, letterbox, bloom, dof, focus);
  }

  public Grade withLetterbox(float v) {
    return new Grade(exposure, contrast, saturation, temperature, vignette, v, bloom, dof, focus);
  }

  public Grade withBloom(float v) {
    return new Grade(
        exposure, contrast, saturation, temperature, vignette, letterbox, v, dof, focus);
  }

  public Grade withDof(float v) {
    return new Grade(
        exposure, contrast, saturation, temperature, vignette, letterbox, bloom, v, focus);
  }

  public Grade withFocus(float v) {
    return new Grade(
        exposure, contrast, saturation, temperature, vignette, letterbox, bloom, dof, v);
  }

  /**
   * Fraction of the frame height covered by each letterbox bar for a frame of {@code width x
   * height}; 0 when the frame is already as wide as the letterbox or there is none.
   */
  public double letterboxBar(int width, int height) {
    if (letterbox <= 0 || width <= 0 || height <= 0) {
      return 0;
    }
    double visible = width / (double) letterbox;
    return visible >= height ? 0 : (height - visible) / 2 / height;
  }
}
