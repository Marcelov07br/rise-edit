package br.com.riseedit.core.shortcuts

/** A key plus modifiers. [key] is a normalized name: "A", "1", "=", "Space", "Left", "Num1"… */
data class KeyChord(val key: String, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false) {
    val label: String
        get() = buildList {
            if (ctrl) add("Ctrl")
            if (alt) add("Alt")
            if (shift) add("Shift")
            add(KEY_LABELS[key] ?: key)
        }.joinToString("+")

    companion object {
        private val KEY_LABELS = mapOf(
            "Space" to "Espaço", "Left" to "←", "Right" to "→", "Up" to "↑", "Down" to "↓",
            "Delete" to "Del", "Backspace" to "⌫", "Esc" to "Esc"
        )
    }
}

enum class ShortcutGroup(val label: String) {
    PLAYBACK("Reprodução e navegação"),
    EDIT("Edição"),
    TIMELINE("Timeline"),
    PANELS("Painéis e arquivo")
}

/** Every Rise Edit command reachable by keyboard. */
enum class RiseAction(val label: String, val group: ShortcutGroup) {
    PLAY_PAUSE("Reproduzir / pausar", ShortcutGroup.PLAYBACK),
    SHUTTLE_REVERSE("Voltar (J)", ShortcutGroup.PLAYBACK),
    SHUTTLE_STOP("Parar (K)", ShortcutGroup.PLAYBACK),
    SHUTTLE_FORWARD("Avançar (L)", ShortcutGroup.PLAYBACK),
    FRAME_BACK("1 quadro para trás", ShortcutGroup.PLAYBACK),
    FRAME_FORWARD("1 quadro para frente", ShortcutGroup.PLAYBACK),
    FRAMES_BACK_5("5 quadros para trás", ShortcutGroup.PLAYBACK),
    FRAMES_FORWARD_5("5 quadros para frente", ShortcutGroup.PLAYBACK),
    SECOND_BACK("1 segundo para trás", ShortcutGroup.PLAYBACK),
    SECOND_FORWARD("1 segundo para frente", ShortcutGroup.PLAYBACK),
    PREV_EDIT("Corte anterior", ShortcutGroup.PLAYBACK),
    NEXT_EDIT("Próximo corte", ShortcutGroup.PLAYBACK),
    GO_START("Início da sequência", ShortcutGroup.PLAYBACK),
    GO_END("Fim da sequência", ShortcutGroup.PLAYBACK),
    CLIP_START("Início do clipe selecionado", ShortcutGroup.PLAYBACK),
    CLIP_END("Fim do clipe selecionado", ShortcutGroup.PLAYBACK),
    RIPPLE_TRIM_START("Ripple trim: início até a agulha", ShortcutGroup.EDIT),
    RIPPLE_TRIM_END("Ripple trim: agulha até o fim", ShortcutGroup.EDIT),
    SPLIT("Dividir na agulha", ShortcutGroup.EDIT),
    DELETE("Excluir", ShortcutGroup.EDIT),
    RIPPLE_DELETE("Excluir com ripple", ShortcutGroup.EDIT),
    DUPLICATE("Duplicar", ShortcutGroup.EDIT),
    COPY("Copiar clipe", ShortcutGroup.EDIT),
    PASTE("Colar clipe", ShortcutGroup.EDIT),
    PASTE_EFFECTS("Colar efeitos (atributos)", ShortcutGroup.EDIT),
    UNDO("Desfazer", ShortcutGroup.EDIT),
    REDO("Refazer", ShortcutGroup.EDIT),
    FREEZE_FRAME("Congelar quadro", ShortcutGroup.EDIT),
    SPEED("Velocidade", ShortcutGroup.EDIT),
    ADD_TEXT("Adicionar texto", ShortcutGroup.EDIT),
    ADD_ADJUSTMENT("Camada de ajuste", ShortcutGroup.EDIT),
    MARKER("Marcador", ShortcutGroup.TIMELINE),
    MARKER_NEXT("Próximo marcador", ShortcutGroup.TIMELINE),
    MARKER_PREV("Marcador anterior", ShortcutGroup.TIMELINE),
    ZOOM_IN("Zoom +", ShortcutGroup.TIMELINE),
    ZOOM_OUT("Zoom −", ShortcutGroup.TIMELINE),
    ZOOM_FIT("Ajustar à sequência", ShortcutGroup.TIMELINE),
    TOGGLE_SNAP("Ímã", ShortcutGroup.TIMELINE),
    EXPORT("Exportar", ShortcutGroup.PANELS),
    IMPORT("Importar mídia", ShortcutGroup.PANELS),
    FULLSCREEN("Tela cheia", ShortcutGroup.PANELS),
    CLOSE_PANEL("Fechar painel", ShortcutGroup.PANELS),
    SHORTCUTS("Atalhos de teclado", ShortcutGroup.PANELS),
    PANEL_1("Painel 1 · Corte", ShortcutGroup.PANELS),
    PANEL_2("Painel 2 · Áudio", ShortcutGroup.PANELS),
    PANEL_3("Painel 3 · Texto", ShortcutGroup.PANELS),
    PANEL_4("Painel 4 · Sobreposição", ShortcutGroup.PANELS),
    PANEL_5("Painel 5 · Cor", ShortcutGroup.PANELS),
    PANEL_6("Painel 6 · Marcadores", ShortcutGroup.PANELS)
}

data class Binding(val chord: KeyChord, val action: RiseAction, val premiereCommand: String? = null)

class Keymap(val name: String, val bindings: List<Binding>) {
    private val byChord = bindings.associateBy { it.chord }
    fun action(chord: KeyChord): RiseAction? = byChord[chord]?.action
    fun chordsFor(action: RiseAction) = bindings.filter { it.action == action }.map { it.chord }

    /** Chords bound to more than one action (must be empty). */
    fun conflicts(): Map<KeyChord, List<RiseAction>> =
        bindings.groupBy { it.chord }.mapValues { (_, b) -> b.map { it.action }.distinct() }.filterValues { it.size > 1 }
}

object Keymaps {
    const val PREMIERE_MARCE = "Premiere do Marce"

    /** Premiere contexts that matter for an editor; timeline wins over global on the same chord. */
    val CONTEXTS = listOf("timeline", "program.monitor", "source.monitor", "global")

    /** Premiere command → Rise Edit action. Anything not here has no Rise equivalent. */
    val PREMIERE_COMMANDS: Map<String, RiseAction> = mapOf(
        "cmd.transport.toggleplay" to RiseAction.PLAY_PAUSE,
        "cmd.transport.shuttle.left" to RiseAction.SHUTTLE_REVERSE,
        "cmd.transport.shuttle.stop" to RiseAction.SHUTTLE_STOP,
        "cmd.transport.shuttle.right" to RiseAction.SHUTTLE_FORWARD,
        "cmd.transport.shuttle.slow.left" to RiseAction.SHUTTLE_REVERSE,
        "cmd.transport.shuttle.slow.right" to RiseAction.SHUTTLE_FORWARD,
        "cmd.transport.step.back" to RiseAction.FRAME_BACK,
        "cmd.transport.step.forward" to RiseAction.FRAME_FORWARD,
        "cmd.transport.step.back.five" to RiseAction.FRAMES_BACK_5,
        "cmd.transport.step.forward.five" to RiseAction.FRAMES_FORWARD_5,
        "cmd.tlnav.prev.edit" to RiseAction.PREV_EDIT,
        "cmd.tlnav.next.edit" to RiseAction.NEXT_EDIT,
        "cmd.tlnav.prev.edit.any.track" to RiseAction.PREV_EDIT,
        "cmd.tlnav.next.edit.any.track" to RiseAction.NEXT_EDIT,
        "cmd.transport.sequence.start" to RiseAction.GO_START,
        "cmd.transport.sequence.end" to RiseAction.GO_END,
        "cmd.transport.selectedclip.start" to RiseAction.CLIP_START,
        "cmd.transport.selectedclip.end" to RiseAction.CLIP_END,
        "cmd.sequence.rippletrimpreviousedittoplayhead" to RiseAction.RIPPLE_TRIM_START,
        "cmd.sequence.rippletrimnextedittoplayhead" to RiseAction.RIPPLE_TRIM_END,
        "cmd.sequence.razorateditline" to RiseAction.SPLIT,
        "cmd.sequence.razorateditline.all" to RiseAction.SPLIT,
        "cmd.edit.clear" to RiseAction.DELETE,
        "cmd.edit.rippledelete" to RiseAction.RIPPLE_DELETE,
        "cmd.timeline.ripple.delete" to RiseAction.RIPPLE_DELETE,
        "cmd.edit.duplicate" to RiseAction.DUPLICATE,
        "cmd.edit.copy" to RiseAction.COPY,
        "cmd.edit.paste" to RiseAction.PASTE,
        "cmd.timeline.paste.to.same.track" to RiseAction.PASTE,
        "cmd.edit.pasteattributes" to RiseAction.PASTE_EFFECTS,
        "cmd.edit.undo" to RiseAction.UNDO,
        "cmd.edit.redo" to RiseAction.REDO,
        "cmd.clip.videooptions.addframehold" to RiseAction.FREEZE_FRAME,
        "cmd.clip.videooptions.insertframeholdsegment" to RiseAction.FREEZE_FRAME,
        "cmd.clip.speed" to RiseAction.SPEED,
        "cmd.graphics.add.text" to RiseAction.ADD_TEXT,
        "cmd.file.new.adjustmentlayer" to RiseAction.ADD_ADJUSTMENT,
        "cmd.set.marker" to RiseAction.MARKER,
        "cmd.marker.gotomarker.next" to RiseAction.MARKER_NEXT,
        "cmd.marker.gotomarker.previous" to RiseAction.MARKER_PREV,
        "cmd.zoom.in" to RiseAction.ZOOM_IN,
        "cmd.zoom.out" to RiseAction.ZOOM_OUT,
        "cmd.tlnav.zoomto.sequence" to RiseAction.ZOOM_FIT,
        "cmd.sequence.snap" to RiseAction.TOGGLE_SNAP,
        "cmd.file.export.movie" to RiseAction.EXPORT,
        "cmd.file.import" to RiseAction.IMPORT,
        "cmd.toggle.fullscreen.monitor" to RiseAction.FULLSCREEN,
        "cmd.edit.keyboardshortcuts" to RiseAction.SHORTCUTS,
        "uif.window.Projects" to RiseAction.PANEL_1,
        "uif.window.Source Monitors" to RiseAction.PANEL_2,
        "uif.window.Timelines" to RiseAction.PANEL_3,
        "uif.window.Program Monitors" to RiseAction.PANEL_4,
        "uif.window.Effect Controls" to RiseAction.PANEL_5,
        "uif.window.Audio Mixers" to RiseAction.PANEL_6
    )

    /**
     * Rise defaults added on top of the Premiere map, only on chords the map leaves free
     * (or binds to a Premiere command Rise does not have, like Ctrl+E "edit original").
     * Shift+arrows stay Premiere's 5 frames; 1 s lives on Ctrl+Alt+arrows.
     */
    val EXTRAS: List<Binding> = listOf(
        Binding(KeyChord("Space"), RiseAction.PLAY_PAUSE),
        Binding(KeyChord("J"), RiseAction.SHUTTLE_REVERSE),
        Binding(KeyChord("K"), RiseAction.SHUTTLE_STOP),
        Binding(KeyChord("L"), RiseAction.SHUTTLE_FORWARD),
        Binding(KeyChord("Left"), RiseAction.FRAME_BACK),
        Binding(KeyChord("Right"), RiseAction.FRAME_FORWARD),
        Binding(KeyChord("Left", shift = true), RiseAction.FRAMES_BACK_5),
        Binding(KeyChord("Right", shift = true), RiseAction.FRAMES_FORWARD_5),
        Binding(KeyChord("Left", ctrl = true, alt = true), RiseAction.SECOND_BACK),
        Binding(KeyChord("Right", ctrl = true, alt = true), RiseAction.SECOND_FORWARD),
        Binding(KeyChord("Z", ctrl = true), RiseAction.UNDO),
        Binding(KeyChord("Z", ctrl = true, shift = true), RiseAction.REDO),
        Binding(KeyChord("C", ctrl = true), RiseAction.COPY),
        Binding(KeyChord("V", ctrl = true), RiseAction.PASTE),
        Binding(KeyChord("Delete"), RiseAction.DELETE),
        Binding(KeyChord("="), RiseAction.ZOOM_IN),
        Binding(KeyChord("+"), RiseAction.ZOOM_IN),
        Binding(KeyChord("Num+"), RiseAction.ZOOM_IN),
        Binding(KeyChord("-"), RiseAction.ZOOM_OUT),
        Binding(KeyChord("Num-"), RiseAction.ZOOM_OUT),
        Binding(KeyChord("E", ctrl = true), RiseAction.EXPORT),
        Binding(KeyChord("Esc"), RiseAction.CLOSE_PANEL)
    ) + (1..6).map { Binding(KeyChord("$it", shift = true), RiseAction.valueOf("PANEL_$it")) }

    /** Builds the preset from a parsed .kys; chords taken by the file are never overridden. */
    fun fromKys(name: String, parsed: List<KysBinding>): Keymap {
        val taken = linkedMapOf<KeyChord, Binding?>()
        for (ctx in CONTEXTS) {
            parsed.filter { it.context == ctx }.forEach { b ->
                if (b.chord !in taken) {
                    taken[b.chord] = PREMIERE_COMMANDS[b.command]?.let { Binding(b.chord, it, b.command) }
                }
            }
        }
        val mapped = taken.values.filterNotNull()
        // A Premiere chord whose command Rise lacks is free for a Rise default.
        val extras = EXTRAS.filter { e -> taken[e.chord] == null && mapped.none { it.chord == e.chord } }
        return Keymap(name, mapped + extras)
    }

    /** Premiere chords whose command has no Rise equivalent. */
    fun unsupported(parsed: List<KysBinding>): List<KysBinding> {
        val seen = mutableSetOf<KeyChord>()
        return CONTEXTS.flatMap { ctx -> parsed.filter { it.context == ctx } }
            .filter { seen.add(it.chord) }
            .filter { it.command !in PREMIERE_COMMANDS }
    }

    fun defaults(): Keymap = Keymap("Padrão Rise", EXTRAS)
}
