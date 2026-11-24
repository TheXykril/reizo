/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

//? if >=26.3 {
/*import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.Optional;
import java.util.OptionalDouble;
*///?} else if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.Optional;
import java.util.OptionalDouble;
*///?} else {
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
//?}
import io.github.thexykril.reizo.core.effects.Grade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Applies the replay's effects ({@link Grade}) to the rendered world, before the HUD is drawn. It
 * runs only while a replay plays and its grade at the current time is not neutral, so it costs
 * nothing otherwise. One full-screen pass: the world picture is copied to a texture and drawn back
 * through {@code reizo:post/grade}, which also reads the world's depth for depth of field (near
 * plane 0.05, as in {@code GameRenderer}).
 */
public final class EffectRenderer {
  private static final int UBO_SIZE = 96; // six vec4

  private static RenderPipeline pipeline;
  private static TextureTarget copy;
  private static MappableRingBuffer ubo;
  private static Grade lastApplied;

  private EffectRenderer() {}

  private static RenderPipeline pipeline() {
    if (pipeline == null) {
      //? if >=26.3 {
      /*pipeline =
          RenderPipelines.register(
              RenderPipeline.builder()
                  .withLocation(Identifier.fromNamespaceAndPath("reizo", "pipeline/grade"))
                  .withVertexShader("core/screenquad")
                  .withFragmentShader(Identifier.fromNamespaceAndPath("reizo", "post/grade_rp"))
                  .withBindGroupLayout(
                      BindGroupLayout.builder()
                          .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                          .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                          .withUniform("ReizoGrade", UniformType.UNIFORM_BUFFER)
                          .build())
                  .withColorTargetState(ColorTargetState.DEFAULT)
                  .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                  .build());
      *///?} else if >=26.2 {
      /*pipeline =
          RenderPipelines.register(
              RenderPipeline.builder()
                  .withLocation(Identifier.fromNamespaceAndPath("reizo", "pipeline/grade"))
                  .withVertexShader("core/screenquad")
                  .withFragmentShader(Identifier.fromNamespaceAndPath("reizo", "post/grade"))
                  .withBindGroupLayout(
                      BindGroupLayout.builder()
                          .withSampler("InSampler")
                          .withSampler("DepthSampler")
                          .withUniform("ReizoGrade", UniformType.UNIFORM_BUFFER)
                          .build())
                  .withColorTargetState(ColorTargetState.DEFAULT)
                  .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                  .build());
      *///?} else if >=26.1 {
      /*// 26.1 sets blending and depth through target states; the defaults are none.
      pipeline =
          RenderPipelines.register(
              RenderPipeline.builder()
                  .withLocation(Identifier.fromNamespaceAndPath("reizo", "pipeline/grade"))
                  .withVertexShader("core/screenquad")
                  .withFragmentShader(Identifier.fromNamespaceAndPath("reizo", "post/grade"))
                  .withSampler("InSampler")
                  .withSampler("DepthSampler")
                  .withUniform("ReizoGrade", UniformType.UNIFORM_BUFFER)
                  .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                  .build());
      *///?} else {
      pipeline =
          RenderPipelines.register(
              RenderPipeline.builder()
                  .withLocation(Identifier.fromNamespaceAndPath("reizo", "pipeline/grade"))
                  .withVertexShader("core/screenquad")
                  .withFragmentShader(Identifier.fromNamespaceAndPath("reizo", "post/grade"))
                  .withSampler("InSampler")
                  .withSampler("DepthSampler")
                  .withUniform("ReizoGrade", UniformType.UNIFORM_BUFFER)
                  .withoutBlend()
                  .withDepthWrite(false)
                  .withDepthTestFunction(
                      com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                  .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                  .build());
      //?}
    }
    return pipeline;
  }

  /** The grade applied in the last frame (for tests), or null when no pass ran. */
  public static Grade lastApplied() {
    return lastApplied;
  }

  /** The world's depth, copied before Minecraft clears it for the hand; null until needed. */
  private static TextureTarget depth;


  /** Whether {@link #depth} holds this frame's world. */
  private static boolean depthKept;

  /**
   * Called after the world is drawn and before Minecraft clears the depth to draw the hand: keeps a
   * copy of the depth while depth of field is on.
   */
  public static void keepDepth(Minecraft mc) {
    if (keptBeforeShaderHand) {
      // A shader pack already kept the world's depth this frame, before drawing its hand.
      keptBeforeShaderHand = false;
      return;
    }
    copyDepth(mc);
  }

  /** Whether the depth was kept by {@link #keepDepthBeforeShaderHand} this frame. */
  private static boolean keptBeforeShaderHand;

  /**
   * Called by Iris (through a mixin) right before a shader pack draws the hand into the world's
   * depth. Later in the frame the depth no longer holds the world, so keep it here.
   */
  public static void keepDepthBeforeShaderHand(Minecraft mc) {
    copyDepth(mc);
    keptBeforeShaderHand = depthKept;
  }

  private static void copyDepth(Minecraft mc) {
    depthKept = false;
    if (!ReplayPlayback.isActive() || ReplayEditor.gradeNow().dof() <= 0) {
      return;
    }
    RenderTarget main = mc.getMainRenderTarget();
    if (main.getDepthTexture() == null) {
      return;
    }
    int w = main.width;
    int h = main.height;
    if (depth == null) {
      //? if >=26.3 {
      /*depth = new TextureTarget("Reizo depth", w, h, null, GpuFormat.D32_FLOAT);
      *///?} else if >=26.2 {
      /*depth = new TextureTarget("Reizo depth", w, h, true, GpuFormat.RGBA8_UNORM);
      *///?} else {
      depth = new TextureTarget("Reizo depth", w, h, true);
      //?}
    } else if (depth.width != w || depth.height != h) {
      depth.resize(w, h);
    }
    depth.copyDepthFrom(main);
    depthKept = true;
  }

  /** The target holding the world's depth for this frame: the kept copy, else the main one. */
  private static RenderTarget depthSource(RenderTarget main) {
    return depthKept ? depth : main;
  }

  /** The set focus distance, or the followed player's distance when focus is automatic. */
  private static float focusDistance(Grade grade) {
    if (grade.focus() > 0) {
      return grade.focus();
    }
    if (ReplayCamera.mode() == ReplayCamera.Mode.FOLLOW && FollowCamera.subjectDistance() > 0) {
      return FollowCamera.subjectDistance();
    }
    return 0;
  }

  /** Called after the world is rendered, before the HUD. */
  public static void afterWorld(Minecraft mc) {
    Grade grade = ReplayEditor.gradeNow();
    // Only while the editor is open: a box left behind by a closed editor never applies.
    float[] box =
        mc.screen instanceof io.github.thexykril.reizo.fabric.ui.EditorView
            ? EditorViewport.rect()
            : null;
    int backdrop = EditorViewport.backdrop();
    if (!ReplayPlayback.isActive() || (grade.isNeutral() && box == null)) {
      lastApplied = null;
      return;
    }
    RenderTarget main = mc.getMainRenderTarget();
    int w = main.width;
    int h = main.height;
    if (copy == null || copy.width != w || copy.height != h) {
      if (copy == null) {
        //? if >=26.3 {
        /*copy = new TextureTarget("Reizo effects", w, h, GpuFormat.RGBA8_UNORM, null);
        *///?} else if >=26.2 {
        /*copy = new TextureTarget("Reizo effects", w, h, false, GpuFormat.RGBA8_UNORM);
        *///?} else {
        copy = new TextureTarget("Reizo effects", w, h, false);
        //?}
      } else {
        copy.resize(w, h);
      }
    }
    if (ubo == null) {
      ubo =
          new MappableRingBuffer(
              () -> "Reizo effects", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
    }
    CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
    encoder.copyTextureToTexture(
        main.getColorTexture(), copy.getColorTexture(), 0, 0, 0, 0, 0, w, h);
    //? if >=26.2 {
    /*// 26.2+ renders with reversed depth: depth 0 is the far plane and depth 1 the near one. The
    // depth stays reversed with an Iris shader pack too (checked with Iris 1.11.7 and
    // Complementary Reimagined in ShaderEffectsGameTest).
    float far = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.depthFar;
    float depth0 = far;
    float depth1 = 0.05f;
    try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
    *///?} else if >=26.1 {
    /*float depth0 = 0.05f;
    float depth1 = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.depthFar;
    try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo.currentBuffer(), false, true)) {
    *///?} else {
    float depth0 = 0.05f;
    float depth1 = mc.gameRenderer.getDepthFar();
    try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo.currentBuffer(), false, true)) {
      //?}
      Std140Builder.intoBuffer(view.data())
          .putVec4(grade.exposure(), grade.contrast(), grade.saturation(), grade.temperature())
          .putVec4(grade.vignette(), (float) grade.letterboxBar(w, h), w / (float) h, 0)
          .putVec4(grade.bloom(), grade.dof(), focusDistance(grade), 0)
          .putVec4(1f / w, 1f / h, depth0, depth1)
          // The shader's texture coordinates start at the bottom; the box's y starts at the top.
          .putVec4(
              box == null ? 0 : box[0],
              box == null ? 0 : 1 - box[1] - box[3],
              box == null ? 1 : box[2],
              box == null ? 1 : box[3])
          .putVec4(
              (backdrop >> 16 & 0xFF) / 255f,
              (backdrop >> 8 & 0xFF) / 255f,
              (backdrop & 0xFF) / 255f,
              1f);
    }
    //? if >=26.3 {
    /*try (RenderPass pass =
        encoder.createRenderPass(
            () -> "Reizo effects",
            main.getColorTextureView(),
            Optional.empty(),
            null,
            OptionalDouble.empty())) {
      pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline()));
      pass.setUniform("ReizoGrade", ubo.currentBuffer());
      pass.setUniform(
          "InSampler",
          copy.getColorTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      pass.setUniform(
          "DepthSampler",
          depthSource(main).getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    *///?} else if >=26.2 {
    /*try (RenderPass pass =
        encoder.createRenderPass(
            () -> "Reizo effects",
            main.getColorTextureView(),
            Optional.empty(),
            null,
            OptionalDouble.empty())) {
      pass.setPipeline(pipeline());
      pass.setUniform("ReizoGrade", ubo.currentBuffer());
      pass.bindTexture(
          "InSampler",
          copy.getColorTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      pass.bindTexture(
          "DepthSampler",
          depthSource(main).getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    *///?} else if >=1.21.11 {
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "Reizo effects", main.getColorTextureView(), OptionalInt.empty())) {
      pass.setPipeline(pipeline());
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("ReizoGrade", ubo.currentBuffer());
      pass.bindTexture(
          "InSampler",
          copy.getColorTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      // The world's depth, for depth of field. The pass writes only color, so reading it is safe.
      pass.bindTexture(
          "DepthSampler",
          depthSource(main).getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(0, 3);
    }
    //?} else {
    /*// 1.21.10 binds textures without a sampler object: the filter lives on the texture.
    copy.getColorTexture().setTextureFilter(FilterMode.LINEAR, false);
    depthSource(main).getDepthTexture().setTextureFilter(FilterMode.NEAREST, false);
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "Reizo effects", main.getColorTextureView(), OptionalInt.empty())) {
      pass.setPipeline(pipeline());
      RenderSystem.bindDefaultUniforms(pass);
      pass.setUniform("ReizoGrade", ubo.currentBuffer());
      pass.bindSampler("InSampler", copy.getColorTextureView());
      // The world's depth, for depth of field. The pass writes only color, so reading it is safe.
      pass.bindSampler("DepthSampler", depthSource(main).getDepthTextureView());
      pass.draw(0, 3);
    }
    *///?}
    ubo.rotate();
    lastApplied = grade.isNeutral() ? null : grade;
  }
}
