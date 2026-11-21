package dev.irltunes.media;

/** Decoded cover pixels in ARGB, ready to upload as a texture on the render thread. */
public record CoverImage(int width, int height, int[] argb) {}
