/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.playback;

//? if >=26.1 {
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
  private static final int UBO_SIZE = 64; // four vec4

  private static RenderPipeline pipeline;
  private static TextureTarget copy;
  private static MappableRingBuffer ubo;
  private static Grade lastApplied;

  private EffectRenderer() {}

  private static RenderPipeline pipeline() {
    if (pipeline == null) {
      //? if >=26.1 {
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

  /** Called after the world is rendered, before the HUD. */
  public static void afterWorld(Minecraft mc) {
    Grade grade = ReplayEditor.gradeNow();
    if (grade.isNeutral() || !ReplayPlayback.isActive()) {
      lastApplied = null;
      return;
    }
    RenderTarget main = mc.getMainRenderTarget();
    int w = main.width;
    int h = main.height;
    if (copy == null || copy.width != w || copy.height != h) {
      if (copy == null) {
        //? if >=26.1 {
        /*copy = new TextureTarget("Reizo effects", w, h, GpuFormat.RGBA8_UNORM, null);
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
    //? if >=26.1 {
    /*// 26.x renders with reversed depth: depth 0 is the far plane and depth 1 the near one.
    float depth0 = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.depthFar;
    float depth1 = 0.05f;
    try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
    *///?} else {
    float depth0 = 0.05f;
    float depth1 = mc.gameRenderer.getDepthFar();
    try (GpuBuffer.MappedView view = encoder.mapBuffer(ubo.currentBuffer(), false, true)) {
      //?}
      Std140Builder.intoBuffer(view.data())
          .putVec4(grade.exposure(), grade.contrast(), grade.saturation(), grade.temperature())
          .putVec4(grade.vignette(), (float) grade.letterboxBar(w, h), w / (float) h, 0)
          .putVec4(grade.bloom(), grade.dof(), grade.focus(), 0)
          .putVec4(1f / w, 1f / h, depth0, depth1);
    }
    //? if >=26.1 {
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
          main.getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(3, 1, 0, 0);
    }
    *///?} else {
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
          main.getDepthTextureView(),
          RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
      pass.draw(0, 3);
    }
    //?}
    ubo.rotate();
    lastApplied = grade;
  }
}
