package br.com.riseedit.core.keys

import br.com.riseedit.core.TransformKeyframe

/** Per-parameter tracks of a 2D transform. */
data class TransformTracks(
    val x: ParameterTrack<Float>,
    val y: ParameterTrack<Float>,
    val scale: ParameterTrack<Float>,
    val rotation: ParameterTrack<Float>,
    val opacity: ParameterTrack<Float>
) {
    fun at(timeMs: Long, base: TransformKeyframe = TransformKeyframe(timeMs)) = TransformKeyframe(
        timeMs,
        x.at(timeMs, base.x),
        y.at(timeMs, base.y),
        scale.at(timeMs, base.scale),
        rotation.at(timeMs, base.rotation),
        opacity.at(timeMs, base.opacity)
    )

    companion object {
        /** Legacy scale/rotation/opacity easing: cubic smoothstep per segment. */
        val LEGACY_EASE: Easing = Easing.CubicBezier.SMOOTHSTEP

        /**
         * Migrates whole-transform keyframes: position keeps its Catmull-Rom Hermite path (Smooth),
         * scale/rotation/opacity keep the smoothstep ease. Evaluation matches the old path exactly.
         */
        fun fromLegacy(keys: List<TransformKeyframe>, domain: TimeDomain): TransformTracks {
            fun track(
                values: TrackValue<Float>,
                easing: Easing,
                pick: (TransformKeyframe) -> Float
            ) = ParameterTrack(domain, keys.map { Key(it.sourceMs, pick(it), easing) }, values)
            return TransformTracks(
                track(TrackValue.Scalar, Easing.Smooth) { it.x },
                track(TrackValue.Scalar, Easing.Smooth) { it.y },
                track(TrackValue.Scalar, LEGACY_EASE) { it.scale },
                track(TrackValue.Angle, LEGACY_EASE) { it.rotation },
                track(TrackValue.Scalar, LEGACY_EASE) { it.opacity }
            )
        }
    }
}
