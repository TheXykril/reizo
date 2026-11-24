/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.core.camera;

/**
 * The follow camera: a camera that glides behind a moving subject instead of being bolted to it.
 *
 * <p>Each update takes the subject's eye position, velocity and facing, and the time that passed.
 * The rig keeps a heading that turns smoothly towards where the subject travels (or faces, when it
 * stands still), wants to sit {@link #distance} behind that heading and {@link #height} above the
 * eyes, and moves there on a critically damped spring, so it catches up without overshooting. It
 * looks at a point a little ahead of the subject, smoothed the same way.
 *
 * <p>Time is the replay's, not the wall clock's: a paused replay holds the camera still, and an
 * export, which steps the replay frame by frame, moves it the way the preview did. Pure math with
 * no game types, so it can be tested on its own.
 */
public final class FollowRig {
  /** Blocks behind the subject. */
  public double distance = 4.5;

  /** Blocks above the subject's eyes. */
  public double height = 0.8;

  /** Seconds the camera takes to settle behind a moved subject; larger is floatier. */
  public double smoothness = 0.45;

  /** Seconds the heading takes to swing round behind a turning subject. */
  public double turnTime = 0.7;

  /** Seconds of the subject's motion the camera looks ahead by. */
  public double lookAhead = 0.3;

  /** Below this speed (blocks per second) the camera stays behind the subject's facing. */
  private static final double WALK = 1.5;

  private boolean placed;
  private double heading; // degrees, Minecraft yaw
  private final double[] pos = new double[3];
  private final double[] vel = new double[3];
  private final double[] look = new double[3];
  private final double[] lookVel = new double[3];

  /** Forgets the camera's state, so the next update places it straight behind the subject. */
  public void reset() {
    placed = false;
  }

  /**
   * Moves the rig on by {@code dt} seconds towards the subject.
   *
   * @param eye the subject's eye position
   * @param velocity the subject's velocity in blocks per second
   * @param yaw the subject's facing (Minecraft yaw, degrees)
   */
  public void update(double[] eye, double[] velocity, double yaw, double dt) {
    double speed = Math.hypot(velocity[0], velocity[2]);
    double wanted = speed > WALK ? Math.toDegrees(Math.atan2(-velocity[0], velocity[2])) : yaw;
    if (!placed) {
      heading = wanted;
    } else if (dt > 0) {
      heading += wrap(wanted - heading) * (1 - Math.exp(-dt * 3 / turnTime));
    }
    double[] goal = behind(eye);
    double[] lookGoal = {
      eye[0] + velocity[0] * lookAhead,
      eye[1] + velocity[1] * lookAhead * 0.5,
      eye[2] + velocity[2] * lookAhead
    };
    if (!placed) {
      System.arraycopy(goal, 0, pos, 0, 3);
      System.arraycopy(lookGoal, 0, look, 0, 3);
      java.util.Arrays.fill(vel, 0);
      java.util.Arrays.fill(lookVel, 0);
      placed = true;
      return;
    }
    if (dt <= 0) {
      return;
    }
    spring(pos, vel, goal, smoothness, dt);
    spring(look, lookVel, lookGoal, smoothness * 0.4, dt);
  }

  /** The camera's position. */
  public double[] position() {
    return pos.clone();
  }

  /** Yaw that looks from the camera at the look point (Minecraft degrees). */
  public float yaw() {
    return (float) Math.toDegrees(Math.atan2(-(look[0] - pos[0]), look[2] - pos[2]));
  }

  /** Pitch that looks from the camera at the look point (Minecraft degrees, down is positive). */
  public float pitch() {
    double dx = look[0] - pos[0];
    double dz = look[2] - pos[2];
    return (float) Math.toDegrees(-Math.atan2(look[1] - pos[1], Math.hypot(dx, dz)));
  }

  /** The point the camera looks at. */
  public double[] lookPoint() {
    return look.clone();
  }

  /** Where the camera wants to be: behind the heading and above the eyes. */
  private double[] behind(double[] eye) {
    double r = Math.toRadians(heading);
    // Minecraft yaw 0 faces +z; forward is (-sin, cos).
    return new double[] {
      eye[0] + Math.sin(r) * distance, eye[1] + height, eye[2] - Math.cos(r) * distance
    };
  }

  /**
   * A critically damped spring step (the closed form, so it moves the same at any frame rate for a
   * subject that holds still over the step).
   */
  private static void spring(double[] x, double[] v, double[] goal, double time, double dt) {
    double omega = 2 / Math.max(time, 1e-3);
    double e = omega * dt;
    double decay = 1 / (1 + e + 0.48 * e * e + 0.235 * e * e * e);
    for (int i = 0; i < 3; i++) {
      double change = x[i] - goal[i];
      double temp = (v[i] + omega * change) * dt;
      v[i] = (v[i] - omega * temp) * decay;
      x[i] = goal[i] + (change + temp) * decay;
    }
  }

  private static double wrap(double degrees) {
    double d = degrees % 360;
    if (d > 180) {
      d -= 360;
    } else if (d < -180) {
      d += 360;
    }
    return d;
  }
}
